package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.R
import com.varuna.openfuel.data.PricePoint
import com.varuna.openfuel.util.Format

/** A line per day; missing days are gaps, not interpolations. */
@Composable
fun HistoryChart(points: List<PricePoint>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val min = points.minOf { it.price }
    val max = points.maxOf { it.price }
    val first = points.first().day
    val last = points.last().day
    val spanDays = (last.toEpochDay() - first.toEpochDay()).coerceAtLeast(1)
    val range = (max - min).takeIf { it > 1e-9 } ?: 0.01

    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.history_max, Format.price(max)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.history_min, Format.price(min)), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(modifier) {
            val padding = 6.dp.toPx()
            val w = size.width - 2 * padding
            val h = size.height - 2 * padding
            fun x(p: PricePoint) = padding + w * (p.day.toEpochDay() - first.toEpochDay()) / spanDays.toFloat()
            fun y(price: Double) = padding + h * (1f - ((price - min) / range).toFloat())

            drawLine(grid, Offset(padding, y(max)), Offset(size.width - padding, y(max)))
            drawLine(grid, Offset(padding, y(min)), Offset(size.width - padding, y(min)))

            val path = Path()
            var previousDay: Long? = null
            points.forEach { p ->
                val d = p.day.toEpochDay()
                val previous = previousDay
                if (previous == null || d - previous > 1) path.moveTo(x(p), y(p.price)) else path.lineTo(x(p), y(p.price))
                previousDay = d
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
            points.lastOrNull()?.let { drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(it), y(it.price))) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Format.day(first), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(Format.day(last), style = MaterialTheme.typography.labelSmall)
        }
    }
}
