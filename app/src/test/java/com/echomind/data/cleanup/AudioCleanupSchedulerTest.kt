package com.echomind.data.cleanup

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.work.ExistingWorkPolicy

class AudioCleanupSchedulerTest {

    @Test
    fun concurrentEnqueueUsesReplacementPolicy() {
        var policy: ExistingWorkPolicy? = null

        AudioCleanupScheduler.enqueue { _, actualPolicy, _ ->
            policy = actualPolicy
        }

        assertEquals(ExistingWorkPolicy.REPLACE, policy)
    }
}
