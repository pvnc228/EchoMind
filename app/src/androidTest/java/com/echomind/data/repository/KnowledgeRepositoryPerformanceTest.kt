package com.echomind.data.repository

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureNanoTime

class KnowledgeRepositoryPerformanceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun linkCandidatePublicSeamRunsProjectionAndRankingOffMain() {
        val dispatcher = RecordingDispatcher()
        val database = inMemoryDatabase()
        try {
            seed(database, 24)
            val repository = KnowledgeRepository(
                database = database,
                knowledgeDao = database.knowledgeDao(),
                settingsStore = SettingsStore(context),
                candidateDispatcher = dispatcher
            )

            val result = runBlocking(Dispatchers.Main) {
                repository.getLinkCandidates(currentRevisionId = 1L)
            }

            assertTrue("The candidate dispatcher was not used", dispatcher.wasInvoked.get())
            assertFalse(
                "Candidate projection/ranking must not execute on the Main looper",
                dispatcher.executedOnMain.get()
            )
            assertTrue(result.isNotEmpty())
            assertTrue(result.size <= 5)
        } finally {
            database.close()
        }
    }

    @Test
    fun linkCandidatePublicSeamMeetsDocumentedTwoSecondBudgetAtOneAndTenThousand() {
        val oneThousandNanos = benchmark(size = 1_000)
        val tenThousandNanos = benchmark(size = 10_000)
        val oneThousandMillis = oneThousandNanos / 1_000_000
        val tenThousandMillis = tenThousandNanos / 1_000_000

        println(
            "LINK_CANDIDATE_BENCHMARK millis: 1k=$oneThousandMillis " +
                "10k=$tenThousandMillis"
        )

        assertTrue(
            "The 10k public candidate operation exceeded the 2,000ms UX budget: " +
                "${tenThousandMillis}ms",
            tenThousandNanos <= 2_000_000_000L
        )
        assertTrue(
            "The 1k public candidate operation exceeded the 2,000ms UX budget: " +
                "${oneThousandMillis}ms",
            oneThousandNanos <= 2_000_000_000L
        )
    }

    private fun benchmark(size: Int): Long {
        val database = inMemoryDatabase()
        try {
            seed(database, size)
            val repository = KnowledgeRepository(
                database = database,
                knowledgeDao = database.knowledgeDao(),
                settingsStore = SettingsStore(context)
            )
            repeat(2) {
                runBlocking(Dispatchers.Main) {
                    repository.getLinkCandidates(currentRevisionId = 1L)
                }
            }
            val samples = List(4) {
                measureNanoTime {
                    val result = runBlocking(Dispatchers.Main) {
                        repository.getLinkCandidates(currentRevisionId = 1L)
                    }
                    check(result.size <= 5) { "Candidate result exceeded the five-item cap" }
                }
            }
            return samples.drop(1).minOrNull() ?: error("No benchmark samples")
        } finally {
            database.close()
        }
    }

    private fun seed(database: AppDatabase, size: Int) {
        val ids = 1L..size.toLong()
        runBlocking {
            database.knowledgeDao().insertRawRecords(
                ids.map { id ->
                    RawRecordEntity(
                        id = id,
                        originalText = if (id == 1L) {
                            "Current reflection source"
                        } else {
                            "career project evidence decision archive $id"
                        },
                        audioPath = null,
                        durationMs = 0L,
                        createdAt = id
                    )
                }
            )
            database.knowledgeDao().insertConclusions(
                listOf(
                    ConclusionEntity(
                        id = 1L,
                        rawRecordId = 1L,
                        currentRevisionId = 1L,
                        createdAt = 1L
                    )
                )
            )
            database.knowledgeDao().insertRevisions(
                listOf(
                    ConclusionRevisionEntity(
                        id = 1L,
                        conclusionId = 1L,
                        version = 1,
                        text = "career project evidence decision",
                        author = "user",
                        createdAt = 1L
                    )
                )
            )
        }
    }

    private fun inMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private class RecordingDispatcher : CoroutineDispatcher() {
        val wasInvoked = AtomicBoolean(false)
        val executedOnMain = AtomicBoolean(false)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            wasInvoked.set(true)
            Dispatchers.Default.dispatch(context) {
                executedOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                block.run()
            }
        }
    }
}
