package com.fplbp.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Exact fraction. Debt shares (350/3 etc.) are summed exactly and rounded
 * only when displayed, so totals never drift.
 */
class Rational private constructor(val num: Long, val den: Long) : Comparable<Rational> {

    operator fun plus(o: Rational): Rational = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Rational): Rational = of(num * o.den - o.num * den, den * o.den)
    operator fun times(n: Long): Rational = of(num * n, den)
    operator fun div(n: Long): Rational = of(num, den * n)

    fun toBigDecimal(scale: Int = 2): BigDecimal =
        BigDecimal(num).divide(BigDecimal(den), scale, RoundingMode.HALF_UP)

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    override fun compareTo(other: Rational): Int = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?): Boolean = other is Rational && num == other.num && den == other.den
    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()
    override fun toString(): String = "$num/$den"

    companion object {
        val ZERO = Rational(0, 1)

        fun of(n: Long, d: Long = 1): Rational {
            require(d != 0L) { "denominator 0" }
            var a = n
            var b = d
            if (b < 0) { a = -a; b = -b }
            val g = gcd(kotlin.math.abs(a), b).coerceAtLeast(1)
            return Rational(a / g, b / g)
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }
}

fun Iterable<Rational>.sumRational(): Rational = fold(Rational.ZERO) { acc, r -> acc + r }
