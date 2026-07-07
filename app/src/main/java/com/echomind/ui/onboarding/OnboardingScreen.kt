package com.echomind.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingPage(
    val icon: @Composable () -> Unit,
    val title: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(
        icon = { Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(64.dp)) },
        title = "Voice Diary",
        description = "Record your thoughts, ideas, and feelings using your voice. Every entry is automatically transcribed."
    ),
    OnboardingPage(
        icon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(64.dp)) },
        title = "AI-Powered Insights",
        description = "Your entries are analyzed by AI to extract tasks, ideas, emotions, and patterns. Ask questions about your past entries."
    ),
    OnboardingPage(
        icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp)) },
        title = "Private & Secure",
        description = "All data stays on your device. Audio files and your database are encrypted. Biometric authentication keeps your diary safe."
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[currentPage]

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) },
            label = "onboarding"
        ) { pageIndex ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                pages[pageIndex].icon()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = pages[pageIndex].title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = pages[pageIndex].description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (currentPage < pages.size - 1) {
                    currentPage++
                } else {
                    onComplete()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (currentPage < pages.size - 1) "Next" else "Get Started")
        }

        if (currentPage < pages.size - 1) {
            TextButton(onClick = onComplete) {
                Text("Skip")
            }
        }
    }
}
