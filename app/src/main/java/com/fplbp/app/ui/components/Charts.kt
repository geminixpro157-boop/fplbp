package com.fplbp.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fplbp.app.ui.theme.App
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ChartSeries(
    val name: String,
    val color: Color,
    val values: List<Double?>,
    val dashed: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier, dashedLast: Boolean = false) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dashedLast && i == items.lastIndex) {
                    Canvas(Modifier.size(14.dp, 10.dp)) {
                        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                    }
                } else {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun niceStep(range: Double, ticks: Int): Double {
    if (range <= 0) return 1.0
    val raw = range / ticks
    val mag = Math.pow(10.0, floor(Math.log10(raw)))
    val n = raw / mag
    val nice = when { n <= 1 -> 1.0; n <= 2 -> 2.0; n <= 5 -> 5.0; else -> 10.0 }
    return nice * mag
}

/**
 * Multi-series line chart with a crosshair + tooltip on tap/drag.
 * Only one y-axis, by design.
 */
@Composable
fun LineChart(
    xLabels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    yFormat: (Double) -> String = { it.roundToInt().toString() },
    startAtZero: Boolean = false,
    invertY: Boolean = false,
    tooltipTitle: (Int) -> String = { xLabels.getOrElse(it) { "" } },
) {
    val colors = App.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = colors.muted
    val tm = rememberTextMeasurer()
    var selected by remember(xLabels, series) { mutableStateOf<Int?>(null) }
    val n = xLabels.size
    if (n == 0) return

    val all = series.flatMap { it.values }.filterNotNull()
    val rawMin = if (startAtZero) 0.0 else (all.minOrNull() ?: 0.0)
    val rawMax = all.maxOrNull() ?: 1.0
    val step = niceStep(max(rawMax - rawMin, 1.0), 4)
    val yMin = floor(rawMin / step) * step
    val yMax = max(ceil(rawMax / step) * step, yMin + step)

    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(n) {
                    detectTapGestures { o ->
                        val left = 40.dp.toPx(); val right = size.width - 12.dp.toPx()
                        val idx = if (n == 1) 0 else (((o.x - left) / (right - left)) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        selected = if (selected == idx) null else idx
                    }
                }
                .pointerInput(n) {
                    detectDragGestures(onDragEnd = {}) { change, _ ->
                        val left = 40.dp.toPx(); val right = size.width - 12.dp.toPx()
                        selected = if (n == 1) 0 else (((change.position.x - left) / (right - left)) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    }
                }
        ) {
            val left = 40.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            fun x(i: Int) = if (n == 1) (left + right) / 2 else left + (right - left) * i / (n - 1)
            fun y(v: Double): Float {
                val f = ((v - yMin) / (yMax - yMin)).toFloat()
                return if (invertY) top + (bottom - top) * f else bottom - (bottom - top) * f
            }
            val labelStyle = TextStyle(color = muted, fontSize = 10.sp)

            // Grid + y labels
            var v = yMin
            while (v <= yMax + 1e-9) {
                val yy = y(v)
                drawLine(colors.grid, Offset(left, yy), Offset(right, yy), 1f)
                val t = tm.measure(yFormat(v), labelStyle)
                drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), yy - t.size.height / 2))
                v += step
            }
            // X labels (thinned)
            val every = max(1, ceil(n / 8.0).toInt())
            for (i in 0 until n) {
                if (i % every != 0 && i != n - 1) continue
                val t = tm.measure(xLabels[i], labelStyle)
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2, bottom + 6.dp.toPx()))
            }
            // Crosshair
            selected?.let { s -> drawLine(muted.copy(alpha = 0.6f), Offset(x(s), top), Offset(x(s), bottom), 1.dp.toPx()) }
            // Lines
            series.forEach { s ->
                val path = Path()
                var started = false
                s.values.forEachIndexed { i, value ->
                    if (value == null) { started = false; return@forEachIndexed }
                    if (!started) { path.moveTo(x(i), y(value)); started = true } else path.lineTo(x(i), y(value))
                }
                drawPath(
                    path, s.color,
                    style = Stroke(
                        width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    ),
                )
                if (n <= 12 && !s.dashed) s.values.forEachIndexed { i, value ->
                    if (value != null) drawCircle(s.color, 3.dp.toPx(), Offset(x(i), y(value)))
                }
                selected?.let { sel ->
                    s.values.getOrNull(sel)?.let {
                        drawCircle(colors.chartSurface, 6.dp.toPx(), Offset(x(sel), y(it)))
                        drawCircle(s.color, 4.5.dp.toPx(), Offset(x(sel), y(it)))
                    }
                }
            }
        }
        selected?.let { sel ->
            val rows = series.mapNotNull { s -> s.values.getOrNull(sel)?.let { Triple(s.name, s.color, it) } }
                .sortedByDescending { if (invertY) -it.third else it.third }
            Tooltip(
                title = tooltipTitle(sel),
                rows = rows.map { Triple(it.first, it.second, yFormat(it.third)) },
                alignRight = sel < n / 2,
            )
        }
    }
}

@Composable
private fun Tooltip(title: String, rows: List<Triple<String, Color, String>>, alignRight: Boolean) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, start = 44.dp, end = 12.dp), contentAlignment = if (alignRight) Alignment.TopEnd else Alignment.TopStart) {
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .width(170.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            rows.forEach { (name, color, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

data class BarItem(val label: String, val value: Double, val color: Color, val valueText: String, val sub: String? = null)

/**
 * Horizontal bars — labels on the left, value at the end of each bar.
 * [onTap] makes rows tappable; [details] instead toggles that content open below the row.
 */
@Composable
fun HBarChart(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 96.dp,
    onTap: ((Int) -> Unit)? = null,
    details: (@Composable (Int) -> Unit)? = null,
) {
    val maxV = items.maxOfOrNull { abs(it.value) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (details != null || onTap != null) 2.dp else 8.dp)) {
        items.forEachIndexed { i, item ->
            var open by remember(item.label) { mutableStateOf(false) }
            val tappable = details != null || onTap != null
            Row(
                Modifier.fillMaxWidth()
                    .then(if (tappable) Modifier.clickable { if (details != null) open = !open else onTap?.invoke(i) } else Modifier)
                    .padding(vertical = if (tappable) 4.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(labelWidth)) {
                    Text(item.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = App.colors.muted, maxLines = 1) }
                }
                Spacer(Modifier.width(8.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    val frac = (abs(item.value) / maxV).toFloat().coerceIn(0f, 1f)
                    if (frac > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth((frac * 0.8f).coerceAtLeast(0.01f))
                                .height(14.dp)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(item.color)
                        )
                    }
                    Text(
                        item.valueText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                    )
                }
                when {
                    details != null -> Icon(
                        if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (open) "Sakrij" else "Prikaži", tint = App.colors.muted,
                    )
                    onTap != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Otvori", tint = App.colors.muted)
                }
            }
            if (details != null) AnimatedVisibility(open) { Box(Modifier.padding(top = 2.dp, bottom = 8.dp)) { details(i) } }
        }
    }
}

data class Segment(val label: String, val value: Int, val color: Color)

/** One stacked bar with 2dp gaps and a legend with values. */
@Composable
fun StackedBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    val positive = segments.filter { it.value > 0 }
    val total = positive.sumOf { it.value }
    Column(modifier) {
        if (total > 0) {
            Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                positive.forEach { s -> Box(Modifier.weight(s.value.toFloat()).height(20.dp).background(s.color)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        positive.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(s.color))
                Spacer(Modifier.width(8.dp))
                Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${s.value}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "  ${(s.value * 100.0 / total).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
                    modifier = Modifier.width(44.dp), textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Vertical bars per gameweek with tap tooltip. Highlighted bars use [highlight]. */
@Composable
fun ColumnChart(
    xLabels: List<String>,
    values: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    highlight: (Int) -> Color? = { null },
    tooltip: (Int) -> String,
) {
    val colors = App.colors
    val tm = rememberTextMeasurer()
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val n = values.size
    if (n == 0) return
    val maxV = max(values.maxOrNull() ?: 1, 1)
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(n) {
                detectTapGestures { o ->
                    val slot = size.width / n.toFloat()
                    val idx = (o.x / slot).toInt().coerceIn(0, n - 1)
                    selected = if (selected == idx) null else idx
                }
            }
        ) {
            val bottom = size.height - 18.dp.toPx()
            val slot = size.width / n
            val barW = min(slot - 2.dp.toPx(), 22.dp.toPx())
            drawLine(colors.grid, Offset(0f, bottom), Offset(size.width, bottom), 1f)
            values.forEachIndexed { i, v ->
                val h = (bottom - 4.dp.toPx()) * v / maxV
                val c = highlight(i) ?: color
                val alpha = if (selected == null || selected == i) 1f else 0.45f
                drawRoundRect(
                    c.copy(alpha = alpha),
                    topLeft = Offset(i * slot + (slot - barW) / 2, bottom - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
            val every = max(1, ceil(n / 10.0).toInt())
            xLabels.forEachIndexed { i, l ->
                if (i % every != 0 && i != n - 1) return@forEachIndexed
                val t = tm.measure(l, TextStyle(color = colors.muted, fontSize = 10.sp))
                drawText(t, topLeft = Offset(i * slot + slot / 2 - t.size.width / 2, bottom + 4.dp.toPx()))
            }
        }
        val sel = selected
        Text(
            if (sel != null) tooltip(sel) else "Dodirni stubac za detalje",
            style = MaterialTheme.typography.labelMedium,
            color = if (sel != null) MaterialTheme.colorScheme.onSurface else colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Team x GW grid of finishing positions. Last place is the strongest color.
 * Every cell carries its number, so color is never the only cue.
 */
/**
 * Team rows of finishing positions per gameweek. Each team's full name sits above its row.
 * Up to [FIT] gameweeks the cells stretch to the card width; beyond that the rows scroll
 * horizontally and open at the latest gameweek.
 */
@Composable
fun PositionHeatmap(rows: List<Pair<String, List<Int?>>>, gws: List<Int>, teamCount: Int, modifier: Modifier = Modifier, colors: List<Color> = emptyList()) {
    val fits = gws.size <= FIT
    val scroll = rememberScrollState()
    LaunchedEffect(gws.size) { if (!fits) scroll.scrollTo(scroll.maxValue) }
    Column(modifier.fillMaxWidth()) {
        val content: @Composable () -> Unit = {
            Column {
                GwHeader(gws, fits)
                rows.forEachIndexed { i, (name, ranks) ->
                    Row(Modifier.padding(top = 8.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        colors.getOrNull(i)?.let {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(it))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(if (fits) Modifier.fillMaxWidth() else Modifier) {
                        ranks.forEach { r -> HeatCell(r, teamCount, if (fits) Modifier.weight(1f) else Modifier.width(30.dp)) }
                    }
                }
            }
        }
        if (fits) content() else Box(Modifier.horizontalScroll(scroll)) { content() }
    }
}

private const val FIT = 12

@Composable
private fun GwHeader(gws: List<Int>, fits: Boolean) {
    Row(if (fits) Modifier.fillMaxWidth() else Modifier) {
        gws.forEach { gw ->
            Box((if (fits) Modifier.weight(1f) else Modifier.width(30.dp)).height(18.dp), contentAlignment = Alignment.Center) {
                Text("$gw", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            }
        }
    }
}

@Composable
private fun HeatCell(r: Int?, teamCount: Int, modifier: Modifier) {
    val heat = App.colors.heat
    val dark = MaterialTheme.colorScheme.background.red < 0.5f
    val idx = if (r == null || teamCount <= 1) 0 else (((r - 1).toFloat() / (teamCount - 1)) * (heat.size - 1)).roundToInt()
    val strong = idx >= heat.size - 2
    Box(
        modifier.padding(1.dp).height(28.dp).clip(RoundedCornerShape(4.dp))
            .background(if (r == null) Color.Transparent else heat[idx]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            r?.toString() ?: "–",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (r == teamCount) FontWeight.Bold else FontWeight.Normal,
            color = if (strong && !dark) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

data class Slice(val label: String, val value: Int, val color: Color)

/** Donut with a 2dp gap between slices; total in the middle, values and shares in the legend. */
@Composable
fun DonutChart(slices: List<Slice>, centerValue: String, centerLabel: String, modifier: Modifier = Modifier) {
    val visible = slices.filter { it.value > 0 }
    val total = visible.sumOf { it.value }.coerceAtLeast(1)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 20.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                val gapDeg = if (visible.size > 1) 2f else 0f
                visible.forEach { s ->
                    val sweep = 360f * s.value / total
                    drawArc(
                        s.color, start + gapDeg / 2, (sweep - gapDeg).coerceAtLeast(0.5f), useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
                    )
                    start += sweep
                }
                if (visible.isEmpty()) drawCircle(surface, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(s.color))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.value}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${(s.value * 100.0 / total).roundToInt()}%", Modifier.width(40.dp),
                        style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
