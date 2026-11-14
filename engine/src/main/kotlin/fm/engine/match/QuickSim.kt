package fm.engine.match

import fm.core.*
import fm.engine.ability.Strength
import kotlin.math.pow

/**
 * Reduced and light match models for matches nobody is watching. Both are calibrated to the
 * same targets as the 2D engine (≈2.7 goals, ≈45% home wins, ≈26% draws) so the detail level
 * does not bias league outcomes.
 */
object QuickSim {
    private const val HOME_ADV = 1.10

    fun expectedGoals(home: TeamSheet, away: TeamSheet, neutral: Boolean): Pair<Double, Double> {
        val sh = Strength.of(home.starters.map { it.first to it.second.position }, home.instructions)
        val sa = Strength.of(away.starters.map { it.first to it.second.position }, away.instructions)
        val adv = if (neutral) 1.0 else HOME_ADV
        val famH = 0.94 + 0.06 * home.familiarity
        val famA = 0.94 + 0.06 * away.familiarity
        val lh = 1.30 * adv * ((sh.attack * famH) / (sa.defense * 0.8 + sa.gk * 0.2)).pow(2.2) * (sh.midfield / sa.midfield).pow(0.8)
        val la = 1.30 / adv * ((sa.attack * famA) / (sh.defense * 0.8 + sh.gk * 0.2)).pow(2.2) * (sa.midfield / sh.midfield).pow(0.8)
        return lh.coerceIn(0.15, 5.0) to la.coerceIn(0.15, 5.0)
    }

    fun play(setup: MatchSetup, detail: Detail): MatchResult {
        val rng = Rng(setup.seed)
        val (lh, la) = expectedGoals(setup.home, setup.away, setup.neutral)
        val sheets = arrayOf(setup.home, setup.away)
        val stats = arrayOf(TeamStats(), TeamStats())
        val events = mutableListOf<MatchEvent>()
        val pm = LinkedHashMap<Long, PlayerMatch>()
        sheets.forEachIndexed { t, s ->
            s.starters.forEach { (p, _) -> pm[p.id] = PlayerMatch(p.id, t).also { it.started = true; it.minutes = 90 } }
        }
        val goals = intArrayOf(0, 0)
        val lambdas = doubleArrayOf(lh, la)
        if (detail == Detail.LIGHT) {
            for (t in 0..1) {
                val g = rng.poisson(lambdas[t])
                repeat(g) { scoreGoal(rng, sheets[t], t, rng.range(1, 90), goals, events, pm) }
                stats[t].shots = (lambdas[t] * 8.5 + rng.range(-2, 3)).toInt().coerceAtLeast(g)
                stats[t].onTarget = (stats[t].shots * 0.36).toInt().coerceAtLeast(g)
                stats[t].xg = lambdas[t]
            }
        } else {
            simulateMinutes(rng, sheets, lambdas, 1, 90, goals, stats, events, pm)
        }
        var et = false
        var penH = -1; var penA = -1
        val aggH = goals[0] + setup.firstLeg.first
        val aggA = goals[1] + setup.firstLeg.second
        if (setup.needsWinner && aggH == aggA) {
            et = true
            simulateMinutes(rng, sheets, doubleArrayOf(lh / 3, la / 3), 91, 120, goals, stats, events, pm)
            pm.values.forEach { if (it.minutes > 0) it.minutes = 120 }
            if (goals[0] + setup.firstLeg.first == goals[1] + setup.firstLeg.second) {
                val (h, a) = Penalties.shootout(rng, setup.home, setup.away)
                penH = h; penA = a
            }
        }
        events.sortBy { it.minute }
        // Ratings
        for ((t, s) in sheets.withIndex()) {
            val gf = goals[t]; val ga = goals[1 - t]
            for ((p, slot) in s.starters) {
                val m = pm.getValue(p.id)
                var r = 6.4 + rng.gaussian() * 0.45 + (gf - ga) * 0.18 + m.goals * 0.9 + m.assists * 0.5
                if (slot.position.line == Line.DEF || slot.position == Position.GK) r += if (ga == 0) 0.6 else -0.15 * ga
                if (m.red) r -= 1.5
                m.rating = r.coerceIn(3.0, 10.0)
                m.energy = (0.55 + 0.25 * p.attr(Attr.STAMINA) / 20.0).coerceIn(0.3, 1.0)
            }
        }
        val res = MatchResult(goals[0], goals[1], et, penH, penA, events, stats, pm, detail)
        res.winner = decideWinner(goals[0] + setup.firstLeg.first, goals[1] + setup.firstLeg.second, penH, penA)
        return res
    }

    fun decideWinner(h: Int, a: Int, penH: Int, penA: Int): Int = when {
        h > a -> 0
        a > h -> 1
        penH >= 0 && penH > penA -> 0
        penA >= 0 && penA > penH -> 1
        else -> -1
    }

    private fun simulateMinutes(
        rng: Rng, sheets: Array<TeamSheet>, lambdas: DoubleArray, from: Int, to: Int, goals: IntArray,
        stats: Array<TeamStats>, events: MutableList<MatchEvent>, pm: MutableMap<Long, PlayerMatch>,
    ) {
        val possH = (0.5 + (lambdas[0] - lambdas[1]) * 0.06).coerceIn(0.3, 0.7)
        for (minute in from..to) {
            for (t in 0..1) {
                val shotsExp = 11.5 * (lambdas[t] / 1.35).pow(0.55)
                val pShot = shotsExp / 90.0
                if (rng.chance(pShot)) {
                    stats[t].shots++
                    val xg = (lambdas[t] / shotsExp) * (0.4 + rng.nextDouble() * 1.2)
                    stats[t].xg += xg
                    if (rng.chance(xg)) {
                        stats[t].onTarget++
                        scoreGoal(rng, sheets[t], t, minute, goals, events, pm)
                    } else if (rng.chance(0.3)) {
                        stats[t].onTarget++; stats[1 - t].saves++
                    }
                }
                if (rng.chance(5.2 / 90)) stats[t].corners++
                if (rng.chance(12.0 / 90)) stats[t].fouls++
                if (rng.chance(1.9 / 90)) card(rng, sheets[t], t, minute, false, stats, events, pm)
                if (rng.chance(0.07 / 90)) card(rng, sheets[t], t, minute, true, stats, events, pm)
                if (rng.chance(0.09 / 90)) {
                    val (p, _) = rng.pick(sheets[t].starters)
                    val m = pm.getValue(p.id)
                    if (m.injuryDays == 0) {
                        val inj = rng.pick(fm.engine.world.NamePools.injuries)
                        m.injuryDays = (inj.second * rng.range(0.6, 1.5)).toInt().coerceAtLeast(2)
                        m.injuryName = inj.first
                        events.add(MatchEvent(minute, EvType.INJURY, t, p.id))
                    }
                }
            }
            stats[0].possessionTicks += Math.round(possH * 600)
            stats[1].possessionTicks += Math.round((1 - possH) * 600)
        }
        for (t in 0..1) {
            stats[t].passes += ((to - from + 1) * (stats[t].possessionTicks.toDouble() / ((to - from + 1) * 600.0)) * 9).toInt()
            stats[t].passesCompleted = (stats[t].passes * 0.78).toInt()
        }
    }

    private fun scorerWeight(p: Player, slot: Slot): Double {
        val posW = when (slot.position.line) { Line.ATT -> if (slot.position == Position.ST) 3.0 else 2.2; Line.MID -> 1.2; Line.DEF -> 0.4; Line.GK -> 0.0 }
        return posW * (0.6 + p.attr(Attr.FINISHING) / 20.0 + 0.3 * p.attr(Attr.OFF_THE_BALL) / 20.0)
    }

    private fun assistWeight(p: Player, slot: Slot): Double {
        val posW = when (slot.position.line) { Line.ATT -> 2.4; Line.MID -> 2.2; Line.DEF -> 0.6; Line.GK -> 0.05 }
        return posW * (0.3 + p.attr(Attr.PASSING) / 20.0 + p.attr(Attr.VISION) / 20.0)
    }

    private fun scoreGoal(rng: Rng, s: TeamSheet, t: Int, minute: Int, goals: IntArray, events: MutableList<MatchEvent>, pm: MutableMap<Long, PlayerMatch>) {
        val on = s.starters.filter { !(pm[it.first.id]?.red ?: false) }
        if (on.isEmpty()) return
        val scorer = rng.weighted(on) { scorerWeight(it.first, it.second) }.first
        goals[t]++
        pm.getValue(scorer.id).goals++
        var assistId = -1L
        if (rng.chance(0.72)) {
            val others = on.filter { it.first.id != scorer.id }
            if (others.isNotEmpty()) {
                val a = rng.weighted(others) { assistWeight(it.first, it.second) }.first
                pm.getValue(a.id).assists++
                assistId = a.id
            }
        }
        events.add(MatchEvent(minute, EvType.GOAL, t, scorer.id, assistId))
    }

    private fun card(rng: Rng, s: TeamSheet, t: Int, minute: Int, red: Boolean, stats: Array<TeamStats>, events: MutableList<MatchEvent>, pm: MutableMap<Long, PlayerMatch>) {
        val on = s.starters.filter { !(pm[it.first.id]?.red ?: false) && it.second.position != Position.GK }
        if (on.isEmpty()) return
        val p = rng.weighted(on) { 0.5 + it.first.attr(Attr.AGGRESSION) / 20.0 }.first
        val m = pm.getValue(p.id)
        if (red || m.yellow == 1) {
            m.red = true; stats[t].reds++
            events.add(MatchEvent(minute, EvType.RED, t, p.id))
        } else {
            m.yellow++; stats[t].yellows++
            events.add(MatchEvent(minute, EvType.YELLOW, t, p.id))
        }
    }
}

object Penalties {
    /** Best-of-five shootout, then sudden death. Returns (home, away) scores. */
    fun shootout(rng: Rng, home: TeamSheet, away: TeamSheet): Pair<Int, Int> {
        fun takers(s: TeamSheet) = s.starters.map { it.first }.sortedByDescending { it.attr(Attr.PENALTY) + it.attr(Attr.COMPOSURE) * 0.5 }
        fun keeper(s: TeamSheet) = s.starters.firstOrNull { it.second.position == Position.GK }?.first
        val th = takers(home); val ta = takers(away)
        val gh = keeper(home); val ga = keeper(away)
        fun scores(taker: Player, gk: Player?): Boolean {
            val skill = (taker.attr(Attr.PENALTY) * 0.7 + taker.attr(Attr.COMPOSURE) * 0.3) / 20.0
            val gkSkill = gk?.let { (it.attr(Attr.REFLEXES) + it.attr(Attr.ONE_ON_ONES)) / 40.0 } ?: 0.3
            return rng.chance((0.74 + 0.16 * (skill - 0.5) - 0.12 * (gkSkill - 0.5)).coerceIn(0.45, 0.93))
        }
        var h = 0; var a = 0
        for (i in 0 until 5) {
            if (scores(th[i % th.size], ga)) h++
            if (h > a + (5 - i)) break
            if (scores(ta[i % ta.size], gh)) a++
            if (a > h + (4 - i) || h > a + (4 - i)) break
        }
        var k = 5
        while (h == a) {
            if (scores(th[k % th.size], ga)) h++
            if (scores(ta[k % ta.size], gh)) a++
            k++
            if (k > 60) { h++; break }
        }
        return h to a
    }
}
