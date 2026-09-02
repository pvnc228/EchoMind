package com.echomind.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.echomind.BuildConfig

@Composable
fun BiometricAuthGate(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Emulators commonly have no enrolled biometric or device credential.
    // Keep authentication active in release builds while letting debug builds
    // reach the app UI for development and testing.
    var isAuthenticated by remember { mutableStateOf(BuildConfig.DEBUG) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!isAuthenticated) {
                        val biometricManager = BiometricManager.from(context)
                        val canAuthenticate = biometricManager.canAuthenticate(
                            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                        )
                        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS ||
                            canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                        ) {
                            val activity = context as FragmentActivity
                            val prompt = BiometricPrompt(
                                activity,
                                ContextCompat.getMainExecutor(context),
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        isAuthenticated = true
                                    }

                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        if (errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT ||
                                            errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                                            errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                                            errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                                        ) {
                                            activity.finish()
                                        }
                                    }

                                    override fun onAuthenticationFailed() {
                                    }
                                }
                            )
                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("EchoMind")
                                .setSubtitle("Authenticate to access your reflections")
                                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                                .build()
                            prompt.authenticate(promptInfo)
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    isAuthenticated = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isAuthenticated) {
        content()
    }
}
