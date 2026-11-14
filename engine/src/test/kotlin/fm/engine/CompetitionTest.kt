package fm.engine

import fm.core.*
import fm.engine.competition.CompetitionEngine
import fm.engine.world.WorldGenerator
import kotlin.test.*

class CompetitionTest {
    @Test
    fun roundRobinEveryPairMeetsHomeAndAway() {
        val teams = (1L..18L).toList()
        val rounds = CompetitionEngine.roundRobin(teams, 2)
        assertEquals(34, rounds.size)
        val pairs = rounds.flatten()
        assertEquals(18 * 17, pairs.size)
        assertEquals(pairs.size, pairs.toSet().size, "no fixture twice")
        for (r in rounds) assertEquals(18, r.flatMap { listOf(it.first, it.second) }.toSet().size, "everyone plays once per round")
    }

    @Test
    fun oddTeamCountGetsByes() {
        val rounds = CompetitionEngine.roundRobin((1L..5L).toList(), 1)
        assertEquals(5, rounds.size)
        assertEquals(10, rounds.flatten().size)
    }

    @Test
    fun worldHasConfiguredLeaguesAndCups() {
        val w = TestWorlds.world()
        for (comp in w.competitions.filter { it.type == CompType.LEAGUE }) {
            assertEquals(comp.league!!.teams, w.compSeasons.getValue(comp.id).teams.size, comp.nameEn)
        }
        val cup = w.compSeasons.getValue(WorldGenerator.IDN_CUP)
        assertEquals(56, cup.teams.size)
        assertEquals(24, cup.fixtures.size, "56 entrants: 8 byes, 24 first-round ties")
        val asia = w.compSeasons.getValue(WorldGenerator.ASIA_CUP)
        assertEquals(16, asia.teams.size)
        assertEquals(16, asia.fixtures.size, "two-legged round of 16")
    }

    @Test
    fun tiebreakersOrderTable() {
        val w = TestWorlds.world()
        val cs = w.compSeasons.getValue(WorldGenerator.IDN_1)
        cs.table[0].points = 10; cs.table[0].gf = 5; cs.table[0].ga = 5
        cs.table[1].points = 10; cs.table[1].gf = 8; cs.table[1].ga = 2
        val sorted = CompetitionEngine.sortedTable(w, cs)
        assertEquals(cs.table[1].clubId, sorted[0].clubId)
    }
}
