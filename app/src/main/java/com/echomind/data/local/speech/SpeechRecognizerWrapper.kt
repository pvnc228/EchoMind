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
        if (!isAvailable()) {
            return@withContext Result.failure(
                OfflineSpeechRecognitionUnavailableException(
                    "On-device speech recognition is not available on this device. " +
                        "Configure Whisper or Gemini in Settings, or type manually."
                )
            )
        }

        val deferred = CompletableDeferred<Result<String>>()

        val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected in audio."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognizer requested network while offline mode was requested."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Record audio permission not granted."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition service is busy."
                    else -> "Speech recognition error code: $error."
                }
                recognizer.destroy()
                if (!deferred.isCompleted) {
                    deferred.complete(Result.failure(IllegalStateException(message)))
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                recognizer.destroy()
                if (!deferred.isCompleted) {
                    if (text.isNotBlank()) {
                        deferred.complete(Result.success(text))
                    } else {
                        deferred.complete(Result.failure(IllegalStateException("No speech recognized.")))
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            recognizer.destroy()
            return@withContext Result.failure(e)
        }

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            deferred.await()
        } ?: run {
            recognizer.destroy()
            Result.failure(IllegalStateException("Speech recognition timed out."))
        }

        result
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
