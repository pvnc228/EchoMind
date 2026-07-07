package com.echomind.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
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
    var hasBiometric by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !isAuthenticated) {
                val biometricManager = BiometricManager.from(context)
                val canAuthenticate = biometricManager.canAuthenticate(
                    BIOMETRIC_STRONG
                )
                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    val activity = context as FragmentActivity
                    val prompt = BiometricPrompt(
                        activity,
                        Executors.newSingleThreadExecutor(),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                isAuthenticated = true
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                ) {
                                    activity.finish()
                                }
                            }

                            override fun onAuthenticationFailed() {
                                // Retry - don't finish
                            }
                        }
                    )
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("EchoMind")
                        .setSubtitle("Authenticate to access your diary")
                        .setAllowedAuthenticators(BIOMETRIC_STRONG)
                        .build()
                    prompt.authenticate(promptInfo)
                } else {
                    hasBiometric = false
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
