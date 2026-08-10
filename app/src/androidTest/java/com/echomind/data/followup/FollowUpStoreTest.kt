package com.echomind.data.followup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FollowUpStoreTest {
    private val files = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cleanup() = runBlocking {
        scopes.forEach { scope ->
            scope.cancel()
            scope.coroutineContext.job.join()
        }
        files.forEach(File::delete)
    }

    @Test
    fun stateSurvivesStoreReopenAndTerminalTransitionsAreIdempotent() = runBlocking {
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "follow-up-${System.nanoTime()}.preferences_pb"
        )
        files += file

        val first = newStore(file)
        assertTrue(first.reserve(7L, 100L))
        assertTrue(first.markScheduled(7L))
        val firstScope = scopes.removeLast()
        firstScope.cancel()
        firstScope.coroutineContext.job.join()

        val reopened = newStore(file)
        assertEquals(FollowUpStatus.SCHEDULED, reopened.get(7L)?.status)
        assertFalse(reopened.reserve(7L, 200L))
        assertTrue(reopened.markFired(7L))
        assertTrue(reopened.markPostponed(7L, 300L))
        assertTrue(reopened.markCanceled(7L))
        assertFalse(reopened.markCanceled(7L))
        assertEquals(FollowUpStatus.CANCELED, reopened.get(7L)?.status)
    }

    private fun newStore(file: File): FollowUpStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return FollowUpStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            ),
            Unit
        )
    }
}
