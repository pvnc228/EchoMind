package com.echomind.data.local.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class OfflineSpeechRecognitionUnavailableException(
    message: String = "On-device speech recognition is not available on this device."
) : IllegalStateException(message)

@Singleton
class SpeechRecognizerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isAvailable(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            true
        }
    }

    suspend fun transcribeAudioFile(audioFile: File): Result<String> = withContext(Dispatchers.Main) {
        Result.failure(
            OfflineSpeechRecognitionUnavailableException(
                "Offline file transcription is not supported by Android SpeechRecognizer. " +
                    "Use Whisper or configure remote transcription in Settings."
            )
        )
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
