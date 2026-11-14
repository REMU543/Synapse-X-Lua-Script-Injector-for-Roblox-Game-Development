package fm.engine

import fm.core.*
import fm.engine.match.*
import fm.engine.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertTrue

class MatchCalibrationTest {
    private data class Agg(var n: Int = 0, var goals: Int = 0, var homeWins: Int = 0, var draws: Int = 0,
                           var shots: Int = 0, var onT: Int = 0, var passes: Int = 0, var passC: Int = 0,
                           var fouls: Int = 0, var yellows: Int = 0, var corners: Int = 0, var offsides: Int = 0, var xg: Double = 0.0)

    private fun run(detail: Detail, matches: Int): Agg {
        val w = TestWorlds.world(7)
        val clubs = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_1 }.sortedBy { it.id }
        val agg = Agg()
        for (k in 0 until matches) {
            val h = clubs[k % clubs.size]; val a = clubs[(k * 7 + 3) % clubs.size]
            if (h.id == a.id) continue
            val setup = MatchSetup(Lineups.pick(w, h), Lineups.pick(w, a), seed = 1000L + k)
            val r = if (detail == Detail.FULL) MatchEngine2D(setup).runToEnd() else QuickSim.play(setup, detail)
            agg.n++; agg.goals += r.homeGoals + r.awayGoals
            if (r.homeGoals > r.awayGoals) agg.homeWins++
            if (r.homeGoals == r.awayGoals) agg.draws++
            for (s in r.stats) {
                agg.shots += s.shots; agg.onT += s.onTarget; agg.passes += s.passes; agg.passC += s.passesCompleted
                agg.fouls += s.fouls; agg.yellows += s.yellows; agg.corners += s.corners; agg.offsides += s.offsides; agg.xg += s.xg
            }
        }
        val n = agg.n.toDouble()
        println("[$detail] n=${agg.n} goals/m=${"%.2f".format(agg.goals / n)} homeWin=${"%.0f".format(100 * agg.homeWins / n)}% " +
            "draw=${"%.0f".format(100 * agg.draws / n)}% shots/team=${"%.1f".format(agg.shots / n / 2)} onT=${"%.1f".format(agg.onT / n / 2)} " +
            "xg/team=${"%.2f".format(agg.xg / n / 2)} passes/team=${"%.0f".format(agg.passes / n / 2)} pass%=${"%.0f".format(100.0 * agg.passC / agg.passes.coerceAtLeast(1))} " +
            "fouls=${"%.1f".format(agg.fouls / n / 2)} yel=${"%.1f".format(agg.yellows / n / 2)} corners=${"%.1f".format(agg.corners / n / 2)} offs=${"%.1f".format(agg.offsides / n / 2)}")
        return agg
    }

    @Test
    fun fullEngineProducesRealisticStatistics() {
        val t0 = System.nanoTime()
        val a = run(Detail.FULL, 60)
        val ms = (System.nanoTime() - t0) / 1e6 / a.n
        println("[FULL] avg ${"%.0f".format(ms)} ms per match")
        val g = a.goals.toDouble() / a.n
        assertTrue(g in 1.6..4.2, "goals per match $g")
        val shots = a.shots.toDouble() / a.n / 2
        assertTrue(shots in 6.0..22.0, "shots per team $shots")
        val passPct = a.passC.toDouble() / a.passes
        assertTrue(passPct in 0.6..0.92, "pass completion $passPct")
    }

    @Test
    fun quickModelsMatchCalibrationTargets() {
        for (d in listOf(Detail.REDUCED, Detail.LIGHT)) {
            val a = run(d, 2000)
            val g = a.goals.toDouble() / a.n
            assertTrue(g in 2.2..3.3, "$d goals per match $g")
            val hw = a.homeWins.toDouble() / a.n
            assertTrue(hw in 0.36..0.54, "$d home win rate $hw")
            val dr = a.draws.toDouble() / a.n
            assertTrue(dr in 0.18..0.34, "$d draw rate $dr")
        }
    }
}
