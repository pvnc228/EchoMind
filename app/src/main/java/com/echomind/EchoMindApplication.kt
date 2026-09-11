package com.echomind

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.echomind.data.cleanup.AudioCleanupScheduler
import com.echomind.data.local.security.AudioEncryptionUtil
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EchoMindApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var audioEncryptionUtil: AudioEncryptionUtil

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AudioCleanupScheduler.enqueue(this)
        CoroutineScope(Dispatchers.IO).launch {
            // Producers cross the same once-only barrier, so a late callback cannot sweep new files.
            runCatching { audioEncryptionUtil.cleanupOrphanedTempFiles() }
                .onFailure { android.util.Log.e("AudioCleanup", "Startup cleanup failed; capture will retry.", it) }
        }
    }
}
