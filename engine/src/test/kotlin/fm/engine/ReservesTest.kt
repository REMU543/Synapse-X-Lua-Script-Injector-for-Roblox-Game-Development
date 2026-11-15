package fm.engine

import fm.core.*
import fm.engine.game.Career
import fm.engine.game.Reserves
import fm.engine.game.Validator
import fm.engine.match.Lineups
import fm.engine.world.WorldGenerator
import kotlin.test.*

class ReservesTest {
    @Test
    fun reservesAreSplitPlayLeagueAndStayOutOfFirstTeam() {
        val club = TestWorlds.world(61).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        val c = Career.newCareer(61, TestWorlds.manager(), club)
        val w = c.world
        // Youth intake makes squads big enough to have reserves; split now.
        fm.engine.development.Development.youthIntake(w)
        for (cl in w.clubs.values) Reserves.autoAssign(w, cl)
        assertTrue(Reserves.reserves(w, club).isNotEmpty())
        assertTrue(Reserves.firstTeam(w, club).size <= Reserves.FIRST_TEAM_SIZE)
        val sheet = Lineups.pick(w, w.club(club))
        assertTrue((sheet.starters.map { it.first } + sheet.bench).none { it.reserve }, "match squad comes from the first team")
        // Move a player manually
        val r = Reserves.reserves(w, club).first()
        c.setReserve(r.id, false)
        assertFalse(r.reserve)
        // Play a couple of months: reserve league advances
        var guard = 0
        while (w.today < Cal.of(2026, 10, 15) && guard++ < 400) { if (c.advanceDay().userMatchPending) c.playUserMatchInstant() }
        val table = Reserves.table(w)
        assertEquals(18, table.size)
        assertTrue(table.all { it.played >= 5 }, "reserve rounds played: ${table.map { it.played }}")
        assertTrue(w.players.values.any { it.reserveApps > 0 })
        assertTrue(Validator.validate(w).isEmpty())
    }
}
