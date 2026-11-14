package fm.engine

import fm.core.*
import fm.engine.game.Career
import fm.engine.game.Validator
import fm.engine.world.WorldGenerator
import kotlin.test.*

class SeasonTest {
    @Test
    fun fullSeasonCompletesWithPromotionRelegationAndCups() {
        val first = TestWorlds.world(11).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        val career = Career.newCareer(11, TestWorlds.manager(), first)
        val w = career.world
        val idn2Before = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_2 }.map { it.id }.toSet()
        var userMatches = 0
        var guard = 0
        while (w.season == 2026 && guard++ < 1000) {
            val r = career.advanceDay()
            if (r.userMatchPending) { career.playUserMatchInstant(); userMatches++ }
        }
        assertEquals(2027, w.season, "season rolled over")
        assertTrue(userMatches >= 34, "user played the league season ($userMatches)")
        val summary = w.seasonHistory.last()
        assertTrue(summary.champions.containsKey(WorldGenerator.IDN_1))
        assertTrue(summary.champions.containsKey(WorldGenerator.IDN_CUP), "cup completed")
        assertTrue(summary.champions.containsKey(WorldGenerator.ASIA_CUP), "continental cup completed")
        val idn1Now = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_1 }.map { it.id }.toSet()
        assertEquals(3, idn1Now.intersect(idn2Before).size, "three clubs promoted")
        assertTrue(w.transfers.isNotEmpty(), "AI clubs made transfers")
        val issues = Validator.validate(w)
        assertTrue(issues.isEmpty(), issues.take(10).joinToString("\n"))
    }
}
