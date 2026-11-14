package fm.engine

import fm.engine.match.*
import fm.engine.world.WorldGenerator
import kotlin.test.Test

/** Manual calibration sweep: ./gradlew :engine:test --tests '*MatchSweepTest*' -Psweep */
class MatchSweepTest {
    @Test
    fun sweep() {
        if (System.getProperty("sweep") == null) return
        val w = TestWorlds.world(7)
        val clubs = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_1 }.sortedBy { it.id }
        for (xs in listOf(0.5, 0.8)) for (rb in listOf(1.0, 0.6)) for (sw in listOf(0.006, 0.004)) {
            MatchEngine2D.LANE_FACTOR = xs; MatchEngine2D.INTERCEPT_FACTOR = rb; MatchEngine2D.PASS_DIST_PENALTY = sw
            var g = 0; var sh = 0; var p = 0; var pc = 0; val n = 30
            for (k in 0 until n) {
                val r = MatchEngine2D(MatchSetup(Lineups.pick(w, clubs[k % 18]), Lineups.pick(w, clubs[(k * 5 + 1) % 18]), 900L + k)).runToEnd()
                g += r.homeGoals + r.awayGoals
                r.stats.forEach { sh += it.shots; p += it.passes; pc += it.passesCompleted }
            }
            println("SWEEP lane=$xs intercept=$rb distPen=$sw goals=${"%.2f".format(g / n.toDouble())} shots/team=${"%.1f".format(sh / n / 2.0)} passes=${p / n / 2} pass%=${100 * pc / p}")
        }
    }
}
