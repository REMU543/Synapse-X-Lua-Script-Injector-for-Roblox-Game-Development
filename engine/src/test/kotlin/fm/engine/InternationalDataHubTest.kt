package fm.engine

import fm.core.*
import fm.engine.game.Career
import fm.engine.game.DataHub
import fm.engine.game.Validator
import fm.engine.international.International
import fm.engine.world.WorldGenerator
import kotlin.test.*

class InternationalDataHubTest {
    @Test
    fun internationalFootballAndDataHubOverHalfASeason() {
        val club = TestWorlds.world(51).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        val c = Career.newCareer(51, TestWorlds.manager(), club)
        val w = c.world
        val end = Cal.of(2027, 1, 10)
        var guard = 0
        while (w.today < end && guard++ < 800) { if (c.advanceDay().userMatchPending) c.playUserMatchInstant() }
        // National teams and call-ups
        assertEquals(w.nations.size, w.nationalTeams.size)
        for (t in w.nationalTeams.values) {
            assertTrue(t.squad.size in 11..International.SQUAD_SIZE, "squad of ${t.nationId}: ${t.squad.size}")
            assertTrue(t.squad.all { w.players[it]?.nationId == t.nationId })
            assertTrue(t.played >= 4, "friendlies & tournament played: ${t.played}")
        }
        assertTrue(w.players.values.any { it.internationalCaps > 0 })
        // Tournament (even season) finished in December with a winner
        val tour = w.tournament
        assertNotNull(tour)
        assertTrue(tour.winner >= 0, "tournament has a winner")
        assertTrue(w.tournamentHistory.isNotEmpty())
        assertEquals(4, International.groupTable(w, 0).size)
        assertTrue(w.intlFixtures.any { it.stage == "F1" && it.played })
        // Data hub
        val avg = DataHub.averages(w, club, WorldGenerator.IDN_1)
        assertNotNull(avg)
        assertTrue(avg.matches >= 15)
        assertTrue(avg.possession in 20.0..80.0)
        val lg = DataHub.leagueAverages(w, WorldGenerator.IDN_1)!!
        assertEquals(lg.goalsFor, lg.goalsAgainst, 0.05, "league goals for == against on average")
        val top = DataHub.leaders(w, WorldGenerator.IDN_1, DataHub.Leader.GOALS)
        assertTrue(top.isNotEmpty() && top.zipWithNext().all { it.first.second >= it.second.second })
        assertTrue(DataHub.matchPoints(w, club).size >= 15)
        assertTrue(Validator.validate(w).isEmpty())
    }
}
