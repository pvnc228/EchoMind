package com.echomind.data.followup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.echomind.data.repository.DecisionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FollowUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val decisionRepository: DecisionRepository,
    private val store: FollowUpStore,
    private val notificationManager: FollowUpNotificationManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val decisionId = inputData.getLong(FollowUpScheduler.DECISION_ID_KEY, -1L)
        if (decisionId <= 0L) return Result.failure()

        if (decisionRepository.getDecision(decisionId) == null) {
            store.markCanceled(decisionId)
            return Result.success()
        }

        if (!store.markFired(decisionId)) return Result.success()
        runCatching { notificationManager.show(decisionId) }
        return Result.success()
    }
}
