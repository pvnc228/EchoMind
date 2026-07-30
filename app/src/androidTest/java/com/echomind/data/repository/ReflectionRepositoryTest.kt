package com.echomind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.domain.model.ReflectionStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReflectionRepositoryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun rawTextIsPersistedBeforeProposalGeneration() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val rawRecordId = repository.captureRawText("My original words")

                assertEquals("My original words", database.knowledgeDao()
                    .getRawRecordById(rawRecordId)?.originalText)
                assertEquals(1, database.entryDao().getAllEntriesOnce().size)
                assertTrue(database.knowledgeDao().getAllHypotheses().isEmpty())
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())

                repository.createLocalProposal(rawRecordId)
                assertEquals(1, database.knowledgeDao().getAllHypotheses().size)
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectionIsDurableAndLeavesNoConclusion() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val rawRecordId = repository.captureRawText("I think this is the only option.")
                val proposal = repository.createLocalProposal(rawRecordId)
                val rejected = repository.reject(proposal.hypothesisId)

                assertEquals(ReflectionStatus.REJECTED, rejected.status)
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())
                assertTrue(database.knowledgeDao().getAllRevisions().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun editedConfirmationCreatesRevisionOneLinkedToSourceAndSurvivesReopen() {
        val firstDatabase = fileDatabase()
        var hypothesisId = 0L
        var rawRecordId = 0L

        try {
            val repository = repository(firstDatabase)
            runBlocking {
                rawRecordId = repository.captureRawText(
                    "I think one rejected draft proves I cannot be an architect."
                )
                val proposal = repository.createLocalProposal(rawRecordId)
                hypothesisId = proposal.hypothesisId
                repository.confirm(
                    hypothesisId,
                    "One rejected draft is evidence about this attempt, not a verdict on my ability."
                )
            }
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = fileDatabase()
        try {
            val restored = runBlocking {
                repository(reopenedDatabase).loadReflection(hypothesisId)
            }

            assertEquals(ReflectionStatus.CONFIRMED, restored.status)
            assertEquals(rawRecordId, restored.rawRecordId)
            assertEquals(1, restored.revisionVersion)
            assertEquals(
                "One rejected draft is evidence about this attempt, not a verdict on my ability.",
                restored.confirmedConclusion
            )
            val conclusion = runBlocking {
                reopenedDatabase.knowledgeDao().getAllConclusions().single()
            }
            assertNotNull(conclusion.currentRevisionId)
            val link = runBlocking {
                reopenedDatabase.knowledgeDao().getAllEvidenceLinks().single()
            }
            assertEquals(rawRecordId, link.sourceRawRecordId)
            assertEquals(ReflectionStatus.CONFIRMED, link.status)
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun proposedReflectionCanBeRestoredAfterReopen() {
        val firstDatabase = fileDatabase()
        try {
            runBlocking {
                val repository = repository(firstDatabase)
                val rawRecordId = repository.captureRawText("I think I need more evidence.")
                repository.createLocalProposal(rawRecordId)
            }
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = fileDatabase()
        try {
            val restored = runBlocking {
                repository(reopenedDatabase).loadLatestProposedReflection()
            }
            assertNotNull(restored)
            assertEquals(ReflectionStatus.PROPOSED, restored?.status)
            assertEquals("I think I need more evidence.", restored?.originalText)
        } finally {
            reopenedDatabase.close()
        }
    }

    private fun repository(database: AppDatabase) = ReflectionRepository(
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        analyzer = LocalReflectionAnalyzer(),
        json = Json { ignoreUnknownKeys = true }
    )

    private fun inMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun fileDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val TEST_DATABASE = "reflection-repository-test.db"
    }
}
