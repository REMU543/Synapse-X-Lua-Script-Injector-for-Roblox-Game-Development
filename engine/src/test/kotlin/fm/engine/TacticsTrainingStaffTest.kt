package fm.engine

import fm.core.*
import fm.engine.ability.RoleFit
import fm.engine.development.Development
import fm.engine.finance.Finance
import fm.engine.game.Career
import fm.engine.match.*
import fm.engine.world.WorldGenerator
import kotlin.test.*

class TacticsTrainingStaffTest {
    private fun career(): Career {
        val club = TestWorlds.world(41).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        return Career.newCareer(41, TestWorlds.manager(), club)
    }

    @Test
    fun rolesAndDutiesAreValidated() {
        val t = Tactic(formation = Formation.F433)
        val st = t.formation.slots.indexOfFirst { it.position == Position.ST }
        val poacher = t.withSlot(st, role = Role.POACHER)
        assertEquals(Role.POACHER, poacher.effectiveSlots()[st].role)
        assertEquals(Duty.ATTACK, poacher.effectiveSlots()[st].duty, "poacher defaults to attack")
        val bad = poacher.withSlot(st, duty = Duty.DEFEND)
        assertEquals(Duty.ATTACK, bad.effectiveSlots()[st].duty, "a poacher cannot defend")
        val wrongRole = t.copy(slotRoles = List(11) { Role.ANCHOR })
        assertEquals(Role.GOALKEEPER, wrongRole.effectiveSlots()[0].role, "invalid roles fall back to defaults")
        val withPi = t.withSlot(st, instr = PlayerInstructions(shootMore = true))
        assertTrue(withPi.effectiveSlots()[st].instructions.shootMore)
    }

    @Test
    fun roleFitRanksRolesForAPlayer() {
        val w = TestWorlds.world(42)
        val st = w.players.values.filter { it.position == Position.ST }.maxBy { it.ca }
        val best = Role.forPosition(Position.ST).maxBy { RoleFit.rating(st, it, Position.ST) }
        assertEquals(5.0, RoleFit.stars(st, best, Position.ST))
        assertTrue(Role.forPosition(Position.ST).all { RoleFit.stars(st, it, Position.ST) in 0.5..5.0 })
        assertTrue(RoleFit.rating(st, Role.POACHER, Position.DC) < RoleFit.rating(st, Role.POACHER, Position.ST))
    }

    @Test
    fun attackingDutiesCreateMoreShotsThanDefendingDuties() {
        val w = TestWorlds.world(43)
        val clubs = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_1 }.sortedBy { it.id }
        fun shots(duty: Duty): Int {
            var total = 0
            for (k in 0 until 16) {
                val h = clubs[k % 18]; val a = clubs[(k + 7) % 18]
                val base = h.tactic.copy(lineup = emptyList())
                var tac = base
                for (i in 1 until 11) tac = tac.withSlot(i, duty = duty)
                val r = MatchEngine2D(MatchSetup(Lineups.pick(w, h, tac), Lineups.pick(w, a), 500L + k)).runToEnd()
                total += r.stats[0].shots
            }
            return total
        }
        val att = shots(Duty.ATTACK); val def = shots(Duty.DEFEND)
        assertTrue(att > def, "attack $att vs defend $def")
    }

    @Test
    fun trainingScheduleShapesDevelopment() {
        val w = TestWorlds.world(44)
        val club = w.clubs.values.first()
        val young = w.squad(club.id).filter { it.age(w.today) <= 22 && it.position != Position.GK }.take(6)
        assertTrue(young.isNotEmpty())
        fun physicalGain(plan: List<Session>): Double {
            val copies = young.map { p -> Player(p.id, p.firstName, p.lastName, p.nationId, p.birthDay, p.position, p.secondary, p.foot, p.heightCm, p.weightKg,
                p.attrs.copyOf(), 200, p.personality, p.injuryProneness, clubId = club.id).also { it.ca = p.ca } }
            club.weeklyPlan = plan.toMutableList()
            val before = copies.sumOf { c -> Attr.entries.filter { it.group == AttrGroup.PHYSICAL }.sumOf { c.attrs[it.ordinal].toInt() } }
            var day = w.today
            repeat(30) { for (c in copies) Development.develop(w, c, club, 12.0, day); day += 7 }
            return (copies.sumOf { c -> Attr.entries.filter { it.group == AttrGroup.PHYSICAL }.sumOf { c.attrs[it.ordinal].toInt() } } - before).toDouble()
        }
        val fitness = physicalGain(List(5) { Session.FITNESS } + listOf(Session.RECOVERY, Session.REST))
        val tactical = physicalGain(List(5) { Session.TACTICAL } + listOf(Session.RECOVERY, Session.REST))
        assertTrue(fitness > tactical * 1.2, "fitness $fitness vs tactical $tactical")
    }

    @Test
    fun retrainingTeachesANewPosition() {
        val w = TestWorlds.world(45)
        val p = w.players.values.first { it.position == Position.MC && Position.DM !in it.secondary }
        p.retrainPosition = Position.DM
        var day = w.today
        repeat(30) { Development.develop(w, p, w.clubs[p.clubId], 10.0, day); day += 7 }
        assertTrue(Position.DM in p.secondary)
        assertNull(p.retrainPosition)
    }

    @Test
    fun hireAndFireStaff() {
        val c = career(); val w = c.world
        val club = w.userClub!!
        val scoutsBefore = club.staffIds.count { w.staff[it]?.role == StaffRole.SCOUT }
        val cand = c.staffMarket(StaffRole.SCOUT).first { it.reputation <= club.reputation + 20 }
        assertNull(c.hireStaff(cand.id))
        assertEquals(scoutsBefore + 1, club.staffIds.count { w.staff[it]?.role == StaffRole.SCOUT })
        val balance = club.balance
        assertTrue(c.fireStaff(cand.id))
        assertTrue(club.balance < balance, "compensation paid")
        assertTrue(Finance.ledgerConsistent(club))
        assertEquals(-1L, cand.clubId)
        // Quota
        repeat(5) { c.staffMarket(StaffRole.PHYSIO).firstOrNull { it.reputation <= club.reputation + 20 }?.let { c.hireStaff(it.id) } }
        assertTrue(club.staffIds.count { w.staff[it]?.role == StaffRole.PHYSIO } <= c.maxStaff(StaffRole.PHYSIO))
    }
}
