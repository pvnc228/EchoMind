package com.echomind.data.followup

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.TimeUnit

class FollowUpSchedulerTest {

    @Test
    fun scheduleUsesOneUniqueWorkItemAndKeepsAnExistingReminder() {
        val now = 1_000_000L
        var actualName: String? = null
        var actualPolicy: ExistingWorkPolicy? = null
        var actualRequest: androidx.work.OneTimeWorkRequest? = null

        FollowUpScheduler.enqueue(
            decisionId = 42L,
            triggerAtMillis = now + TimeUnit.DAYS.toMillis(2),
            nowMillis = now,
            enqueueUniqueWork = { name, policy, request ->
                actualName = name
                actualPolicy = policy
                actualRequest = request
            }
        )

        assertEquals("follow-up-42", actualName)
        assertEquals(ExistingWorkPolicy.KEEP, actualPolicy)
        assertEquals(TimeUnit.DAYS.toMillis(2), actualRequest?.workSpec?.initialDelay)
        assertEquals(
            42L,
            actualRequest?.workSpec?.input?.getLong(FollowUpScheduler.DECISION_ID_KEY, -1L)
        )
    }

    @Test
    fun scheduleRejectsDelaysOutsideTheOwnerContract() {
        assertThrows(IllegalArgumentException::class.java) {
            FollowUpScheduler.enqueue(
                decisionId = 42L,
                triggerAtMillis = 1_000_000L + TimeUnit.HOURS.toMillis(12),
                nowMillis = 1_000_000L,
                enqueueUniqueWork = { _, _, _ -> }
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FollowUpScheduler.enqueue(
                decisionId = 42L,
                triggerAtMillis = 1_000_000L + TimeUnit.DAYS.toMillis(4),
                nowMillis = 1_000_000L,
                enqueueUniqueWork = { _, _, _ -> }
            )
        }
    }

    @Test
    fun postponeReplacesTheCompletedReminderWithTheNewDueTime() {
        var actualPolicy: ExistingWorkPolicy? = null

        FollowUpScheduler.enqueue(
            decisionId = 42L,
            triggerAtMillis = 1_000_000L + TimeUnit.DAYS.toMillis(1),
            nowMillis = 1_000_000L,
            policy = ExistingWorkPolicy.REPLACE,
            enqueueUniqueWork = { _, policy, _ -> actualPolicy = policy }
        )

        assertEquals(ExistingWorkPolicy.REPLACE, actualPolicy)
    }
}
