package com.echomind.data.followup

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object FollowUpScheduler {
    const val DECISION_ID_KEY = "decision_id"
    const val UNIQUE_WORK_PREFIX = "follow-up-"
    val MIN_DELAY_MILLIS: Long = TimeUnit.DAYS.toMillis(1)
    val MAX_DELAY_MILLIS: Long = TimeUnit.DAYS.toMillis(3)

    fun uniqueWorkName(decisionId: Long): String = "$UNIQUE_WORK_PREFIX$decisionId"

    fun schedule(
        context: Context,
        decisionId: Long,
        triggerAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        enqueue(
            decisionId = decisionId,
            triggerAtMillis = triggerAtMillis,
            nowMillis = nowMillis,
            enqueueUniqueWork = { name, policy, request ->
                WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
            }
        )
    }

    fun postpone(
        context: Context,
        decisionId: Long,
        triggerAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        enqueue(
            decisionId = decisionId,
            triggerAtMillis = triggerAtMillis,
            nowMillis = nowMillis,
            policy = ExistingWorkPolicy.REPLACE,
            enqueueUniqueWork = { name, policy, request ->
                WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
            }
        )
    }

    fun cancel(context: Context, decisionId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(decisionId))
    }

    internal fun enqueue(
        decisionId: Long,
        triggerAtMillis: Long,
        nowMillis: Long,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        enqueueUniqueWork: (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit
    ) {
        require(decisionId > 0) { "A follow-up needs a valid decision ID." }
        val delayMillis = triggerAtMillis - nowMillis
        require(delayMillis in MIN_DELAY_MILLIS..MAX_DELAY_MILLIS) {
            "A follow-up must be scheduled between one and three days from now."
        }
        val request = OneTimeWorkRequestBuilder<FollowUpWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DECISION_ID_KEY to decisionId))
            .build()
        enqueueUniqueWork(uniqueWorkName(decisionId), policy, request)
    }
}
