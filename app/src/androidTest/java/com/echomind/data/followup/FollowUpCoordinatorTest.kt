package com.echomind.data.followup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.repository.DecisionRepository
import com.echomind.data.repository.ReflectionRepository
import com.echomind.data.followup.FollowUpStatus.SCHEDULED
import com.echomind.data.followup.FollowUpStatus.CANCELED
import com.echomind.data.followup.FollowUpStatus.POSTPONED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FollowUpCoordinatorTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: AppDatabase? = null
    private var storeFile: File? = null
    private var storeScope: CoroutineScope? = null
    private var store: FollowUpStore? = null
    private var workName: String? = null

    @After
    fun cleanup() {
        workName?.let { WorkManager.getInstance(context).cancelUniqueWork(it) }
        database?.close()
        storeScope?.cancel()
        storeFile?.delete()
        store = null
    }

    @Test
    fun scheduleRequiresChoiceAndDuplicateSchedulingKeepsOneWorkItem() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val reflection = reflectionRepository(database!!)
        val repository = DecisionRepository(database!!, database!!.knowledgeDao(), reflection)
        val decisionId = repository.createDecision(
            question = "Follow-up decision",
            sourceRevisionId = createRevision(reflection)
        )
        val coordinator = coordinator(repository)

        var rejected = false
        try {
            coordinator.schedule(decisionId, 1)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue("A reminder must require an explicit choice", rejected)

        repository.setChoice(decisionId, "Continue")
        val scheduled = coordinator.schedule(decisionId, 1)
        assertEquals(SCHEDULED, scheduled.status)
        workName = FollowUpScheduler.uniqueWorkName(decisionId)

        var duplicateRejected = false
        try {
            coordinator.schedule(decisionId, 2)
        } catch (_: IllegalStateException) {
            duplicateRejected = true
        }
        assertTrue("A second active reminder must be rejected", duplicateRejected)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(workName!!)
            .get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)

        assertTrue(checkNotNull(store).markFired(decisionId))
        val postponed = coordinator.postpone(decisionId)
        assertEquals(POSTPONED, postponed.status)
        assertTrue(coordinator.cancel(decisionId))
        assertEquals(CANCELED, checkNotNull(store).get(decisionId)?.status)
    }

    private suspend fun createRevision(reflection: ReflectionRepository): Long {
        val rawId = reflection.captureRawText("A source for the follow-up test")
        val proposal = reflection.createLocalProposal(rawId)
        return requireNotNull(reflection.confirm(proposal.hypothesisId, "A confirmed conclusion").revisionId)
    }

    private fun coordinator(repository: DecisionRepository): FollowUpCoordinator {
        val file = File(context.cacheDir, "follow-up-coordinator-${System.nanoTime()}.preferences_pb")
        storeFile = file
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeScope = scope
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        store = FollowUpStore(dataStore, Unit)
        return FollowUpCoordinator(
            context = context,
            decisionRepository = repository,
            store = checkNotNull(store),
            notificationManager = FollowUpNotificationManager(context),
            ioDispatcher = Dispatchers.IO
        )
    }

    private fun reflectionRepository(database: AppDatabase) = ReflectionRepository(
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        analyzer = LocalReflectionAnalyzer(),
        json = Json { ignoreUnknownKeys = true }
    )
}
