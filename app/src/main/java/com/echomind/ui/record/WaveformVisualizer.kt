package com.echomind.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF6C5CE7),
    inactiveColor: Color = Color(0xFFD0BCFF),
    barCount: Int = 60
) {
    val displayValues = if (amplitudes.size >= barCount) {
        // Subsample to fit bar count
        val step = amplitudes.size / barCount.toFloat()
        (0 until barCount).map { i ->
            val idx = (i * step).toInt()
            amplitudes[idx.coerceIn(0, amplitudes.lastIndex)]
        }
    } else {
        amplitudes + List(barCount - amplitudes.size) { 0f }
    }

    val maxAmp = displayValues.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier = modifier) {
        val barWidth = size.width / barCount
        val gap = barWidth * 0.25f
        val usableWidth = barWidth - gap

        displayValues.forEachIndexed { index, amplitude ->
            val fraction = (amplitude / maxAmp).coerceIn(0f, 1f)
            val barHeight = fraction * size.height
            val x = index * barWidth + gap / 2

            val isActive = amplitude > 0f

            drawRect(
                color = if (isActive) barColor else inactiveColor,
                topLeft = Offset(x, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(usableWidth, barHeight.coerceAtLeast(1f))
            )
        }
    }
}
