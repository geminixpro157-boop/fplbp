package com.fplbp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.GwStatus
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Pos
import com.fplbp.app.ui.theme.App

/** Color follows the team, never its rank: assigned by entry id order, which never changes. */
@Composable
fun teamColor(data: LeagueData, entryId: Long): Color {
    val series = App.colors.series
    val idx = data.teams.map { it.entryId }.sorted().indexOf(entryId).coerceAtLeast(0)
    return series[idx % series.size]
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    /** Soft gold gradient in the background (used on the top cards only). */
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glow = App.colors.gold.copy(alpha = 0.16f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.then(if (accent) Modifier.drawBehind { drawAccent(glow) } else Modifier).padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
                Spacer(Modifier.size(12.dp))
            }
            content()
        }
    }
}

@Composable
fun StatusBadge(status: GwStatus) {
    val (text, color) = when (status) {
        GwStatus.FINAL -> "Završeno" to App.colors.safe
        GwStatus.AWAITING -> "Čeka potvrdu" to App.colors.provisional
        GwStatus.LIVE -> "Privremeno" to App.colors.provisional
    }
    Pill(text, color)
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(50)))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text, style = MaterialTheme.typography.labelSmall,
            color = if (filled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Club short name, tinted by position (GK/DEF/MID/FWD). No logos. */
@Composable
fun ClubChip(club: String, elementType: Int?) {
    val c = when (elementType) {
        Pos.GK -> Color(0xFFC98500)
        Pos.DEF -> Color(0xFF199E70)
        Pos.MID -> Color(0xFF3987E5)
        Pos.FWD -> Color(0xFFD55181)
        else -> App.colors.muted
    }
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(c.copy(alpha = 0.16f)).padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(c))
        Spacer(Modifier.width(4.dp))
        Text(
            "$club · ${elementType?.let { Pos.short(it) } ?: ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TeamDot(color: Color) = Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = App.colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface, sub: String? = null) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = App.colors.muted) }
    }
}

/** Diagonal glow from the top-left corner fading out toward the bottom-right. */
private fun DrawScope.drawAccent(color: Color) {
    drawRect(
        Brush.linearGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            start = Offset.Zero,
            end = Offset(size.width * 0.85f, size.height * 1.1f),
        )
    )
}
