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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echomind.R

private data class OnboardingPage(
    val icon: @Composable () -> Unit,
    val titleRes: Int,
    val descriptionRes: Int
)

private val pages = listOf(
    OnboardingPage(
        icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(64.dp)) },
        titleRes = R.string.onboarding_text_first_title,
        descriptionRes = R.string.onboarding_text_first_description
    ),
    OnboardingPage(
        icon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(64.dp)) },
        titleRes = R.string.onboarding_insights_title,
        descriptionRes = R.string.onboarding_insights_description
    ),
    OnboardingPage(
        icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp)) },
        titleRes = R.string.onboarding_private_title,
        descriptionRes = R.string.onboarding_private_description
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
                val page = pages[pageIndex]
                page.icon()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(page.descriptionRes),
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
