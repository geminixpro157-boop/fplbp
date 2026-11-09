package com.fplbp.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.TransferAnalysis
import com.fplbp.app.ui.components.ClubChip
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.Pill
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

fun signed(n: Int) = if (n > 0) "+$n" else "$n"

@Composable
fun signColor(n: Int): Color = when {
    n > 0 -> App.colors.safe
    n < 0 -> App.colors.debt
    else -> MaterialTheme.colorScheme.onSurface
}

/** Serbian count words: 1 transfer, 2 transfera, 5 transfera, 21 transfer. */
private fun count(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $w"
}

private fun transfera(n: Int) = count(n, "transfer", "transfera", "transfera")
private fun kola(n: Int) = count(n, "kolo", "kola", "kola")

@Composable
fun TransfersScreen(data: LeagueData) {
    if (data.transfers.isEmpty() && data.transferSummary.values.all { it.freeHits.isEmpty() }) {
        return EmptyState("Još nema transfera (prvi su mogući od kola 2).")
    }
    var selected by rememberSaveable { mutableLongStateOf(data.teams.firstOrNull()?.entryId ?: 0L) }
    var byImpact by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Overview(data) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.teams.forEach { t ->
                    FilterChip(
                        selected = selected == t.entryId,
                        onClick = { selected = t.entryId },
                        label = { Text(t.name) },
                        leadingIcon = { TeamDot(teamColor(data, t.entryId)) },
                    )
                }
            }
        }
        item { TeamSummary(data, selected) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Po kolu", "Po učinku").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = byImpact == i, onClick = { byImpact = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {},
                    ) { Text(label) }
                }
            }
        }
        val list = data.transfers.filter { it.entryId == selected }
        if (list.isEmpty()) {
            item { Text("Nijedan transfer.", style = MaterialTheme.typography.bodySmall, color = App.colors.muted) }
        } else if (byImpact == 1) {
            item {
                SectionCard(title = "Od najboljeg do najgoreg", subtitle = "Dok je novi igrač bio u timu") {
                    val sorted = list.sortedByDescending { it.heldDiff }
                    sorted.forEachIndexed { i, r ->
                        TransferCard(data, r, held = true, showGw = true)
                        if (i < sorted.lastIndex) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = App.colors.grid)
                    }
                }
            }
        } else {
            list.groupBy { it.gw }.toList().sortedByDescending { it.first }.forEach { (gw, rs) ->
                item(key = "gw-$selected-$gw") { GwGroup(data, selected, gw, rs) }
            }
        }
    }
}

@Composable
private fun Overview(data: LeagueData) {
    var explain by rememberSaveable { mutableStateOf(false) }
    val rows = data.transferSummary.values.sortedByDescending { it.netAfterHits }
    SectionCard(title = "Transferi u sezoni") {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("Tim", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            Header("Transfera", 58)
            Header("Razlika", 52)
            Header("Hitovi", 46)
            Header("Ukupno", 54)
        }
        HorizontalDivider(color = App.colors.grid)
        rows.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    TeamDot(teamColor(data, s.entryId))
                    Spacer(Modifier.width(8.dp))
                    Text(data.teamName(s.entryId), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Cell("${s.transfers}", 58)
                Cell(signed(s.heldNet), 52, signColor(s.heldNet))
                Cell(if (s.hits > 0) "−${s.hits}" else "0", 46, if (s.hits > 0) App.colors.debt else App.colors.muted)
                Cell(signed(s.netAfterHits), 54, signColor(s.netAfterHits), bold = true)
            }
        }
        Text(
            if (explain) "Sakrij objašnjenje" else "Kako se računa?",
            style = MaterialTheme.typography.labelMedium, color = App.colors.gold,
            modifier = Modifier.clickable { explain = !explain }.padding(top = 8.dp, bottom = 2.dp),
        )
        AnimatedVisibility(explain) {
            Text(
                "Za svaki transfer gledamo kola dok je novi igrač bio u timu i poredimo njegove bodove sa bodovima " +
                    "prodatog igrača u tim istim kolima (bez kapitenskog množenja). Razlika = zbir tih poređenja. " +
                    "Ukupno = razlika minus hitovi. Free Hit kolo se ne računa kao transferi, već se ceo tim poredi " +
                    "sa timom iz prethodnog kola.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TeamSummary(data: LeagueData, entryId: Long) {
    val s = data.transferSummary[entryId] ?: return
    val mine = data.transfers.filter { it.entryId == entryId }
    val inPts = mine.sumOf { it.heldIn }
    val outPts = mine.sumOf { it.heldOut }
    SectionCard {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(data.teamName(entryId)) }
                append(": ${transfera(s.transfers)}. Novi igrači doneli ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$inPts") }
                append(", prodati bi u istim kolima doneli ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$outPts") }
                append(" → ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = signColor(s.heldNet))) { append(signed(s.heldNet)) }
                append(". Hitovi ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (s.hits > 0) App.colors.debt else App.colors.muted)) {
                    append(if (s.hits > 0) "−${s.hits}" else "0")
                }
                append(". Ukupno ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = signColor(s.netAfterHits))) { append(signed(s.netAfterHits)) }
                append(".")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        s.freeHits.forEach { fh ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Free Hit · kolo ${fh.gw}", App.colors.gold)
                Spacer(Modifier.width(8.dp))
                Text("${fh.points} b. umesto ${fh.wouldHave} sa prošlonedeljnim timom", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                Text(signed(fh.diff), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = signColor(fh.diff))
            }
        }
    }
}

@Composable
private fun GwGroup(data: LeagueData, entryId: Long, gw: Int, rs: List<TransferAnalysis.Result>) {
    val total = rs.sumOf { it.heldDiff }
    val hit = data.gws.firstOrNull { it.gw == gw }?.scores?.firstOrNull { it.entryId == entryId }?.transferCost ?: 0
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kolo $gw", style = MaterialTheme.typography.titleMedium)
            if (rs.any { it.wildcard }) { Spacer(Modifier.width(8.dp)); Pill("Wildcard", App.colors.gold) }
            Spacer(Modifier.weight(1f))
            if (rs.size > 1) Text("${transfera(rs.size)} · ukupno ", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
            if (rs.size > 1) Text(signed(total), style = MaterialTheme.typography.labelLarge, color = signColor(total), fontWeight = FontWeight.Bold)
        }
        if (hit > 0) Text("hit −$hit", style = MaterialTheme.typography.labelMedium, color = App.colors.debt)
        Spacer(Modifier.height(6.dp))
        rs.sortedByDescending { it.heldDiff }.forEachIndexed { i, r ->
            TransferCard(data, r, held = true, showGw = false)
            if (i < rs.lastIndex) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = App.colors.grid)
        }
    }
}

/**
 * One transfer as a two-row comparison: ↑ player in vs ↓ player out, with a verdict.
 * [held] = points while the new player was kept; otherwise only the transfer's own gameweek.
 */
@Composable
fun TransferCard(data: LeagueData, r: TransferAnalysis.Result, held: Boolean, showGw: Boolean) {
    val inPts = if (held) r.heldIn else r.weekIn
    val outPts = if (held) r.heldOut else r.weekOut
    val diff = inPts - outPts
    val lastGw = data.gws.lastOrNull()?.gw ?: r.gw
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showGw) {
                Text("Kolo ${r.gw}", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                if (r.wildcard) { Spacer(Modifier.width(6.dp)); Pill("WC", App.colors.gold) }
            }
            Spacer(Modifier.weight(1f))
            Text(signed(diff), style = MaterialTheme.typography.titleMedium, color = signColor(diff), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(
                when { diff > 0 -> "isplatilo se"; diff < 0 -> "nije se isplatilo"; else -> "nerešeno" },
                style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
            )
        }
        val max = maxOf(inPts, outPts, 1)
        PlayerLine(data, "↑", "Ušao", r.elementIn, inPts, max, App.colors.safe)
        PlayerLine(data, "↓", "Izašao", r.elementOut, outPts, max, App.colors.debt)
        val footer = if (held) {
            val kept = r.heldGws
            val status = when {
                kept.isEmpty() -> null
                kept.last() >= lastGw -> "još u timu"
                else -> "prodat u kolu ${kept.last() + 1}"
            }
            buildList {
                when (kept.size) {
                    0 -> add("nije igrao za tim")
                    1 -> add("1 kolo u timu (K${kept[0]})")
                    else -> add("${kola(kept.size)} u timu (K${kept.first()}–K${kept.last()})")
                }
                status?.let { add(it) }
                if (kept.size > 1) add("u kolu ${r.gw}, ${data.playerName(r.elementIn)} ${r.weekIn} a ${data.playerName(r.elementOut)} ${r.weekOut}")
            }.joinToString(" · ")
        } else {
            "bodovi u ovom kolu"
        }
        Text(footer, style = MaterialTheme.typography.labelSmall, color = App.colors.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PlayerLine(data: LeagueData, arrow: String, label: String, element: Int, pts: Int, max: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(arrow, Modifier.width(14.dp), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(label, Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = color)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.playerName(element), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                ClubChip(data.clubShort(element), data.players[element]?.elementType)
            }
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier.fillMaxWidth((pts.coerceAtLeast(0).toFloat() / max).coerceIn(0.02f, 1f)).height(5.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(color.copy(alpha = 0.75f))
            )
        }
        Text("$pts b.", Modifier.width(52.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Header(text: String, width: Int) =
    Text(text, Modifier.width(width.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End)

@Composable
private fun Cell(text: String, width: Int, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) =
    Text(
        text, Modifier.width(width.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
        color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
