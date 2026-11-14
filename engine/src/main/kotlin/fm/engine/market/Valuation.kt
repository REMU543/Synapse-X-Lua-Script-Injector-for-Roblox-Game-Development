package fm.engine.market

import fm.core.*
import kotlin.math.exp

/**
 * Wages and market values are pure functions of ability, age, potential, contract and the
 * economy of the nation. Anchoring everything to a per-nation wage index keeps the economy
 * stable over decades (no runaway inflation).
 */
object Valuation {
    private fun roundTo(v: Double, step: Long): Long = (Math.round(v / step) * step).coerceAtLeast(step)

    /** Fair monthly wage for a player of [ca] at a club of [clubRep] in [nation]. */
    fun wage(ca: Int, nation: Nation, clubRep: Int): Long {
        val base = nation.wageIndex * exp((ca - 60) / 25.0)
        val repF = 0.75 + 0.5 * clubRep / 100.0
        return roundTo(base * repF, 500_000)
    }

    fun youthWage(nation: Nation): Long = roundTo(nation.wageIndex * 0.15, 100_000)

    fun ageFactor(age: Int): Double = when {
        age <= 19 -> 1.5
        age <= 21 -> 1.45
        age <= 24 -> 1.35
        age <= 27 -> 1.2
        age <= 29 -> 1.0
        age <= 31 -> 0.7
        age <= 33 -> 0.4
        else -> 0.2
    }

    fun value(p: Player, nation: Nation, clubRep: Int, today: Int, season: Int): Long {
        val age = p.age(today)
        val annual = wage(p.ca, nation, clubRep) * 12.0
        val years = ((p.contract?.endYear ?: season) - season).coerceAtLeast(0)
        val contractF = when (years) { 0 -> 0.35; 1 -> 0.65; 2 -> 0.85; else -> 1.0 }
        val potential = if (age <= 23) {
            val head = (p.pa - p.ca).coerceAtLeast(0)
            wage(minOf(200, p.ca + head / 2), nation, clubRep) * 12.0 * 0.6 * (head / 40.0).coerceAtMost(1.5)
        } else 0.0
        val v = annual * 2.0 * ageFactor(age) * contractF + potential
        return if (v < 50_000_000) roundTo(v, 5_000_000) else roundTo(v, 25_000_000)
    }
}
