package com.echomind.data.cleanup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AudioCleanupScheduler {
    const val UNIQUE_WORK_NAME = "audio-cleanup"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AudioCleanupWorker>()
            .setConstraints(Constraints.Builder().build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10L,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
