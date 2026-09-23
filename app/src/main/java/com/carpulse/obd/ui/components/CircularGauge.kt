package com.carpulse.obd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CircularGauge(
    title: String,
    value: Float?,
    unit: String,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    arcColor: Color = MaterialTheme.colorScheme.primary,
    warnValue: Float? = null,
    dangerValue: Float? = null,
    lowerIsWorse: Boolean = false,
    placeholder: String = "—",
    decimals: Int = 0
) {
    // === 1. Цвета и значения вычисляем ДО Canvas — здесь @Composable-контекст ===
    val errorColor = MaterialTheme.colorScheme.error
    val warnColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val unitColor = MaterialTheme.colorScheme.onSurfaceVariant

    val color = when {
        value == null -> MaterialTheme.colorScheme.outlineVariant

        lowerIsWorse -> when {
            dangerValue != null && value <= dangerValue -> errorColor
            warnValue != null && value <= warnValue -> warnColor
            else -> arcColor
        }

        else -> when {
            dangerValue != null && value >= dangerValue -> errorColor
            warnValue != null && value >= warnValue -> warnColor
            else -> arcColor
        }
    }

    val progress = when {
        value == null -> 0f
        max - min <= 0f -> 0f
        else -> ((value - min) / (max - min)).coerceIn(0f, 1f)
    }

    val displayValue = when {
        value == null -> placeholder
        decimals == 0 -> value.toInt().toString()
        else -> "%.${decimals}f".format(value)
    }

    // === 2. UI ===
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // В Canvas используются только вычисленные переменные — никакого MaterialTheme
            Canvas(Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.10f
                val inset = strokeWidth / 2f
                val arcSize = Size(
                    size.width - strokeWidth,
                    size.height - strokeWidth
                )
                val topLeft = Offset(inset, inset)

                val startAngle = 135f
                val maxSweep = 270f

                // Фоновая дуга — trackColor уже вычислен вне Canvas
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = maxSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Заполненная дуга — color тоже вычислен заранее
                if (progress > 0f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = maxSweep * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Значение и единица
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    displayValue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    unit,
                    fontSize = 11.sp,
                    color = unitColor
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = unitColor
        )
    }
}