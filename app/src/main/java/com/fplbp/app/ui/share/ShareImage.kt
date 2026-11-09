package com.fplbp.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import com.fplbp.app.BuildConfig
import com.fplbp.app.data.GwStatus
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.Rational
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a PNG of the debt table or a gameweek and opens the share sheet
 * (Viber, WhatsApp…). Always drawn in the dark theme so it looks the same for everyone.
 */
object ShareImage {

    private const val W = 1080
    private const val PAD = 64f
    private const val ROW = 112f

    private const val BG_TOP = 0xFF241F12.toInt()
    private const val BG = 0xFF0F141A.toInt()
    private const val CARD = 0xFF18202A.toInt()
    private const val TEXT = 0xFFE6EAEE.toInt()
    private const val MUTED = 0xFF8A96A3.toInt()
    private const val GOLD = 0xFFE8B84A.toInt()
    private const val DEBT = 0xFFFF7A6B.toInt()
    private const val SAFE = 0xFF4ADE80.toInt()
    private const val AMBER = 0xFFF5A524.toInt()
    private const val GRID = 0xFF2A3440.toInt()

    /** The gameweek to offer for sharing: the live one, or else the latest. */
    fun shareableGw(data: LeagueData): GwView? = data.liveGw ?: data.gws.lastOrNull()

    fun shareDebtTable(context: Context, data: LeagueData) =
        share(context, renderDebtTable(data), "fplbp-dug.png")

    fun shareGameweek(context: Context, data: LeagueData, gv: GwView) =
        share(context, renderGameweek(data, gv), "fplbp-kolo-${gv.gw}.png")

    // ------------------------------------------------------------ render

    private fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }

    private fun ellipsize(text: String, p: TextPaint, width: Float) =
        TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    private fun canvas(rows: Int, extra: Float): Pair<Bitmap, Canvas> {
        val h = (PAD + 210f + 40f + rows * ROW + extra + 120f + PAD).toInt()
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(BG_TOP, BG, BG), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), h.toFloat(), bg)
        return bmp to c
    }

    private fun header(c: Canvas, data: LeagueData, title: String, sub: String): Float {
        var y = PAD + 40f
        c.drawText(data.leagueName.uppercase(), PAD, y, paint(34f, GOLD, bold = true).apply { letterSpacing = 0.12f })
        y += 84f
        c.drawText(title, PAD, y, paint(68f, TEXT, bold = true))
        y += 56f
        c.drawText(sub, PAD, y, paint(36f, MUTED))
        return y + 40f
    }

    private fun card(c: Canvas, top: Float, rows: Int): Float {
        val r = RectF(PAD - 24f, top, W - PAD + 24f, top + rows * ROW + 24f)
        c.drawRoundRect(r, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        return top + 12f
    }

    private fun footer(c: Canvas, y: Float, text: String) {
        c.drawText(text, PAD, y, paint(30f, MUTED))
        c.drawText("FPLBP", W - PAD, y, paint(30f, GOLD, bold = true, align = Paint.Align.RIGHT))
    }

    fun renderDebtTable(data: LeagueData): Bitmap {
        val rows = data.debtTable
        val (bmp, c) = canvas(rows.size, 0f)
        val lastFinal = data.gws.lastOrNull { it.isFinal }?.gw
        val top = header(
            c, data, "Tabela dugovanja",
            (if (lastFinal != null) "Posle kola $lastFinal · " else "") + "Kasa: ${Fmt.rsdShort(data.pot)}",
        )
        var y = card(c, top, rows.size)
        val max = rows.maxOfOrNull { it.total } ?: Rational.ZERO
        val name = paint(44f, TEXT, bold = true)
        val small = paint(30f, MUTED)
        rows.forEachIndexed { i, r ->
            val team = data.teamById[r.entryId]
            val color = when {
                r.total == Rational.ZERO -> SAFE
                r.total == max -> DEBT
                else -> TEXT
            }
            val base = y + ROW / 2
            c.drawText("${i + 1}", PAD, base + 14f, paint(40f, MUTED, bold = true))
            c.drawText(ellipsize(team?.name ?: "?", name, 520f), PAD + 64f, base, name)
            c.drawText(ellipsize("${team?.manager ?: ""} · poslednji ×${r.timesLast}", small, 520f), PAD + 64f, base + 40f, small)
            c.drawText(Fmt.rsdShort(r.total), W - PAD, base + 16f, paint(46f, color, bold = true, align = Paint.Align.RIGHT))
            if (i < rows.lastIndex) c.drawLine(PAD, y + ROW, W - PAD, y + ROW, Paint().apply { this.color = GRID; strokeWidth = 2f })
            y += ROW
        }
        footer(c, y + 100f, "${data.finalCount} kola × ${Fmt.rsdShort(data.debt)} · ${today()}")
        return bmp
    }

    fun renderGameweek(data: LeagueData, gv: GwView): Bitmap {
        val scores = gv.scores.sortedByDescending { it.net }
        val o = gv.outcome
        val (bmp, c) = canvas(scores.size, 90f)
        val status = when (gv.status) {
            GwStatus.FINAL -> "Završeno"
            GwStatus.AWAITING -> "Čeka potvrdu FPL-a"
            GwStatus.LIVE -> "Uživo · utakmice ${gv.fixturesDone}/${gv.fixturesTotal}"
        }
        val top = header(c, data, "Kolo ${gv.gw}", status)
        var y = card(c, top, scores.size)
        val name = paint(44f, TEXT, bold = true)
        val small = paint(30f, MUTED)
        scores.forEachIndexed { i, s ->
            val loser = o != null && s.entryId in o.losers
            val base = y + ROW / 2
            c.drawText("${o?.ranks?.get(s.entryId) ?: i + 1}", PAD, base + 14f, paint(40f, MUTED, bold = true))
            c.drawText(ellipsize(data.teamName(s.entryId), name.apply { color = if (loser) DEBT else TEXT }, 560f), PAD + 64f, base, name)
            // Only what the big number doesn't already say: hits and chips.
            val detail = buildList {
                if (s.transferCost > 0) add("${s.points} b. − ${s.transferCost} hit")
                s.chip?.let { add(com.fplbp.app.domain.Chips.label(it)) }
            }.joinToString(" · ")
            c.drawText(ellipsize(detail, small, 560f), PAD + 64f, base + 40f, small)
            c.drawText("${s.net}", W - PAD, base + 16f, paint(54f, if (loser) DEBT else TEXT, bold = true, align = Paint.Align.RIGHT))
            if (i < scores.lastIndex) c.drawLine(PAD, y + ROW, W - PAD, y + ROW, Paint().apply { color = GRID; strokeWidth = 2f })
            y += ROW
        }
        if (o != null) {
            val who = o.losers.joinToString(" i ") { data.teamName(it) }
            val verb = if (gv.isFinal) "Duguje" else "Trenutno duguje"
            val amount = Fmt.rsdShort(o.share) + if (o.losers.size > 1) " svako" else ""
            c.drawText(ellipsize("$verb: $who · $amount", paint(40f, if (gv.isFinal) DEBT else AMBER, bold = true), W - 2 * PAD),
                PAD, y + 90f, paint(40f, if (gv.isFinal) DEBT else AMBER, bold = true))
        }
        footer(c, y + 190f, if (gv.isFinal) today() else "Privremeno · ${today()}")
        return bmp
    }

    private fun today() = SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault()).format(Date())

    // ------------------------------------------------------------ app link

    /** Always downloads the newest APK (the release workflow uploads a copy named FPLBP.apk). */
    fun appLink(): String? = BuildConfig.GITHUB_REPO.takeIf { it.isNotBlank() }
        ?.let { "https://github.com/$it/releases/latest/download/FPLBP.apk" }

    fun shareAppLink(context: Context) {
        val link = appLink() ?: return
        val text = "FPLBP – aplikacija za našu FPL ligu: tabela dugovanja, uživo kolo i statistika.\n" +
            "Preuzmi najnoviju verziju: $link\n" +
            "Posle preuzimanja otvori fajl i dozvoli instalaciju iz nepoznatih izvora."
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Podeli aplikaciju").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------ share

    private fun share(context: Context, bmp: Bitmap, fileName: String) {
        try {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Podeli").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Deljenje nije uspelo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
