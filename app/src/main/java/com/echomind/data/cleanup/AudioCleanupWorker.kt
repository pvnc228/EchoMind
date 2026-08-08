package com.echomind.data.cleanup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.echomind.data.repository.EntryRepository

@HiltWorker
class AudioCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val entryRepository: EntryRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val pending = entryRepository.getPendingAudioCleanup()
            if (pending.isEmpty()) {
                return@runCatching Result.success()
            }

            entryRepository.retryPendingAudioCleanup()
            val remaining = entryRepository.getPendingAudioCleanup()
            when {
                remaining.isEmpty() -> Result.success()
                remaining.any { it.attemptCount < EntryRepository.MAX_AUDIO_CLEANUP_ATTEMPTS } ->
                    Result.retry()
                else -> Result.failure()
            }
        }.getOrElse { Result.retry() }
    }
}
