package fm.engine

import fm.core.*
import fm.engine.development.Development
import fm.engine.finance.Finance
import fm.engine.game.Career
import fm.engine.market.Transfers
import fm.engine.world.WorldGenerator
import kotlin.test.*

class MarketAndDevelopmentTest {
    private fun career(): Career {
        val club = TestWorlds.world(21).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        return Career.newCareer(21, TestWorlds.manager(), club)
    }

    @Test
    fun userCanBuyAPlayerAndMoneyMoves() {
        val c = career(); val w = c.world
        val user = w.userClub!!
        user.transferBudget = 100 * Money.MILIAR
        val target = w.players.values.filter { it.clubId >= 0 && it.clubId != user.id && w.club(it.clubId).nationId == 1 }
            .filter { w.squad(it.clubId).count { s -> s.position.line == it.position.line } > 6 }.minBy { it.value }
        val seller = w.club(target.clubId)
        val sellerBalance = seller.balance
        val fee = target.value * 2
        val wage = Transfers.wageDemand(w, target, user) * 2
        val r = c.offer(target.id, fee, wage, 3)
        assertTrue(r is Transfers.OfferResult.Accepted, "offer result $r")
        assertEquals(user.id, target.clubId)
        assertTrue(target.id in user.playerIds && target.id !in seller.playerIds)
        assertEquals(sellerBalance + fee, seller.balance)
        assertTrue(Finance.ledgerConsistent(user) && Finance.ledgerConsistent(seller))
    }

    @Test
    fun lowBidIsRejectedOrCountered() {
        val c = career(); val w = c.world
        val user = w.userClub!!
        user.transferBudget = 100 * Money.MILIAR
        val target = w.players.values.filter { it.clubId >= 0 && it.clubId != user.id }.maxBy { it.value }
        val r = c.offer(target.id, target.value / 10, target.contract!!.wage * 2, 3)
        assertTrue(r is Transfers.OfferResult.Rejected || r is Transfers.OfferResult.Counter, "got $r")
    }

    @Test
    fun youngPlayersGrowAndOldPlayersDecline() {
        val w = TestWorlds.world(22)
        val young = w.players.values.filter { it.age(w.today) <= 18 && it.pa - it.ca > 30 }.take(20)
        val old = w.players.values.filter { it.age(w.today) >= 33 }.take(20)
        assertTrue(young.isNotEmpty() && old.isNotEmpty())
        val y0 = young.associate { it.id to it.ca }; val o0 = old.associate { it.id to it.ca }
        repeat(52) { Development.weekly(w); w.today += 7 }
        assertTrue(young.count { it.ca > y0.getValue(it.id) } >= young.size * 3 / 4, "most youngsters improved")
        assertTrue(old.count { it.ca < o0.getValue(it.id) } >= old.size * 3 / 4, "most veterans declined")
        assertTrue(young.all { it.ca <= it.pa + 1 }, "potential is a ceiling")
    }

    @Test
    fun retirementAndYouthIntake() {
        val w = TestWorlds.world(23)
        val veteran = w.players.values.maxBy { it.age(w.today) }
        assertTrue(Development.retirementChance(veteran, w.today + 365 * 4) > 0.4, "veterans retire")
        assertEquals(0.0, Development.retirementChance(w.players.values.minBy { it.age(w.today) }, w.today))
        val before = w.players.size
        val youth = Development.youthIntake(w)
        assertTrue(youth.size > 200, "every club gets youngsters (${youth.size})")
        val academy = youth.filter { it.clubId >= 0 }
        assertTrue(academy.all { it.age(w.today) in 15..17 }, "academy ages " + academy.map { it.age(w.today) }.distinct())
        assertTrue(academy.all { it.contract?.youth == true })
        assertEquals(before + youth.size, w.players.size)
        val retired = Development.retirements(w)
        assertTrue(retired.isNotEmpty(), "someone retires")
        assertTrue(retired.none { it.id in w.players })
    }
}
