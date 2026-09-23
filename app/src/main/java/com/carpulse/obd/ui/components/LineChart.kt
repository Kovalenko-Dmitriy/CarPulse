package com.carpulse.obd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Простой график по списку значений.
 * Масштаб по вертикали подстраивается автоматически.
 */
@Composable
fun LineChart(
    data: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (data.size < 2) {
            Text(
                "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val max = data.maxOrNull() ?: 1f
            val min = data.minOrNull() ?: 0f
            val range = (max - min).coerceAtLeast(1f)

            val dx = size.width / (data.size - 1)
            val topPad = 12f
            val botPad = 12f
            val h = size.height - topPad - botPad

            // Сетка — 4 горизонтальные линии
            val gridColor = Color.LightGray.copy(alpha = 0.25f)
            for (i in 0..4) {
                val y = topPad + h * i / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            // Линия данных
            val path = Path()
            data.forEachIndexed { i, v ->
                val x = i * dx
                val y = topPad + h * (1f - (v - min) / range)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3f))

            // Текущая точка
            val last = data.last()
            val lastX = (data.size - 1) * dx
            val lastY = topPad + h * (1f - (last - min) / range)
            drawCircle(color = lineColor, radius = 6f, center = Offset(lastX, lastY))
        }
    }
}