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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.Executors

@Composable
fun BiometricAuthGate(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAuthenticated by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !isAuthenticated) {
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
                        Executors.newSingleThreadExecutor(),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                isAuthenticated = true
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                // Only finish on hard errors (device lockout, no auth configured)
                                if (errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT ||
                                    errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                                    errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                                    errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                                ) {
                                    activity.finish()
                                }
                                // ERROR_NEGATIVE_BUTTON and ERROR_USER_CANCELED
                                // let the user retry via the prompt on next ON_RESUME
                            }

                            override fun onAuthenticationFailed() {
                                // Retry - don't finish
                            }
                        }
                    )
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("EchoMind")
                        .setSubtitle("Authenticate to access your diary")
                        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                        .apply {
                            // Show "Use PIN" as negative button only if device credential
                            // is not already the primary option
                            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                                setNegativeButtonText("Cancel")
                            }
                        }
                        .build()
                    prompt.authenticate(promptInfo)
                } else {
                    // No biometric or device credential available at all
                    isAuthenticated = true
                }
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
