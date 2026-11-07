package com.fplbp.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Serbian number formatting: 1.234,56 */
object Fmt {

    fun rsd(r: Rational): String = money(r.toBigDecimal(2)) + " RSD"

    fun rsd(amount: Long): String = money(BigDecimal(amount).setScale(2)) + " RSD"

    /** Whole-number amounts without decimals, e.g. "350 RSD"; otherwise "116,67 RSD". */
    fun rsdShort(r: Rational): String {
        val bd = r.toBigDecimal(2)
        return if (bd.stripTrailingZeros().scale() <= 0) group(bd.toBigInteger().toString()) + " RSD"
        else money(bd) + " RSD"
    }

    fun money(bd: BigDecimal): String {
        val s = bd.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val neg = s.startsWith("-")
        val body = s.removePrefix("-")
        val (intPart, frac) = body.split(".").let { it[0] to it.getOrElse(1) { "00" } }
        return (if (neg) "-" else "") + group(intPart) + "," + frac
    }

    fun decimal(v: Double, scale: Int = 1): String {
        val bd = BigDecimal(v).setScale(scale, RoundingMode.HALF_UP).toPlainString()
        val neg = bd.startsWith("-")
        val body = bd.removePrefix("-")
        val parts = body.split(".")
        val res = group(parts[0]) + if (parts.size > 1) "," + parts[1] else ""
        return if (neg) "-$res" else res
    }

    private fun group(intPart: String): String {
        val sb = StringBuilder()
        intPart.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.reverse().toString()
    }

    fun firstName(managerName: String): String = managerName.trim().split(" ").firstOrNull().orEmpty()
}
