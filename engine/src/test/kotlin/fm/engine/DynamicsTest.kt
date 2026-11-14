package fm.engine

import fm.core.*
import fm.engine.dynamics.*
import fm.engine.game.Career
import fm.engine.game.MatchDay
import fm.engine.world.WorldGenerator
import kotlin.test.*

class DynamicsTest {
    private fun career(): Career {
        val club = TestWorlds.world(31).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        return Career.newCareer(31, TestWorlds.manager(), club)
    }

    @Test
    fun hierarchyHasLeadersAndInfluenceOrdering() {
        val c = career(); val w = c.world
        val h = Dynamics.hierarchy(w, w.manager.clubId)
        assertEquals(w.squad(w.manager.clubId).size, h.size)
        assertEquals(2, h.count { it.tier == Dynamics.Tier.LEADER })
        assertTrue(h.zipWithNext().all { it.first.influence >= it.second.influence })
        assertTrue(Dynamics.socialGroups(w, w.manager.clubId, Lang.ID).isNotEmpty())
    }

    @Test
    fun benchedKeyPlayerBecomesUnhappyAndRequestsTransfer() {
        val c = career(); val w = c.world
        val star = w.squad(w.manager.clubId).maxBy { it.ca }
        // Simulate: the club played 10 matches, the key player none.
        val cs = w.compSeasons.getValue(w.userClub!!.leagueId)
        cs.fixtures.filter { it.homeId == w.manager.clubId || it.awayId == w.manager.clubId }.take(10).forEach { it.played = true }
        star.contract = star.contract!!.copy(squadStatus = SquadStatus.KEY)
        repeat(10) { Dynamics.weekly(w) }
        assertTrue(star.happiness < 45, "happiness ${star.happiness}")
        assertTrue(Dynamics.factors(w, star).any { it.keyEn == "Lack of playing time" })
        assertTrue(star.wantsOut, "transfer request after weeks of unhappiness")
        assertTrue(w.inbox.any { it.key == "dyn_transfer_request" })
        assertTrue(Interactions.Topic.TRANSFER_REQUEST in Interactions.available(w, star))
    }

    @Test
    fun brokenPromiseHurtsRelationshipAndDressingRoom() {
        val c = career(); val w = c.world
        val p = w.squad(w.manager.clubId).first()
        val rel = p.relationship
        val o = Interactions.reply(w, p, Interactions.Reply.PROMISE_TIME)
        assertTrue(o.positive)
        assertEquals(1, w.promises.size)
        w.today += 80
        Dynamics.checkPromises(w)
        assertTrue(w.promises.first().resolved && !w.promises.first().kept)
        assertTrue(p.relationship < rel)
        assertTrue(Dynamics.factors(w, p).any { it.keyEn == "Broken promise" })
    }

    @Test
    fun conversationsDependOnPersonality() {
        val c = career(); val w = c.world
        val squad = w.squad(w.manager.clubId)
        val calm = squad.first(); val hot = squad.last()
        val calmP = Personality(16, 10, 10, 18, 10, 10, 10, 10)
        val hotP = Personality(6, 10, 10, 3, 10, 10, 10, 10)
        val f = Player::class.java.getDeclaredField("personality"); f.isAccessible = true
        f.set(calm, calmP); f.set(hot, hotP)
        calm.season.apps = 5; calm.season.ratingSum = 5 * 620
        hot.season.apps = 5; hot.season.ratingSum = 5 * 620
        assertTrue(Interactions.talk(w, calm, Interactions.Topic.CRITICISE_FORM).positive)
        assertFalse(Interactions.talk(w, hot, Interactions.Topic.CRITICISE_FORM).positive)
        assertFalse(Interactions.canTalk(w, hot), "cooldown after a chat")
    }

    @Test
    fun teamTalkSuggestionsFitTheSituation() {
        assertEquals(TeamTalk.Tone.PRAISE, TeamTalk.suggest(TeamTalk.Stage.FULL, 2, true))
        assertNotEquals(TeamTalk.Tone.CRITICISE, TeamTalk.suggest(TeamTalk.Stage.HALF, 1, false))
        assertTrue(TeamTalk.base(TeamTalk.Tone.NO_PRESSURE, TeamTalk.Stage.PRE, 0, false) > TeamTalk.base(TeamTalk.Tone.DEMAND_MORE, TeamTalk.Stage.PRE, 0, false))
    }

    @Test
    fun fullMatchDayFlow() {
        val c = career(); val w = c.world
        var guard = 0
        while (c.userFixtureToday() == null && guard++ < 60) c.advanceDay()
        val f = c.userFixtureToday()!!
        val md = MatchDay(c, f)
        assertTrue(md.preview.winPct + md.preview.drawPct + md.preview.lossPct in 98..102)
        val press = md.pressPre()
        assertTrue(press.questions.isNotEmpty())
        press.questions.forEach { Press.answer(w, it, Press.Answer.CAUTIOUS) }
        assertNull(w.pendingPress)
        val pre = md.talk(TeamTalk.Stage.PRE, md.suggestedTone(TeamTalk.Stage.PRE))
        assertTrue(pre.size >= 11)
        val e = md.start()
        while (e.events.none { it.type == fm.engine.match.EvType.HALFTIME }) e.step()
        md.talk(TeamTalk.Stage.HALF, TeamTalk.Tone.PASSIONATE)
        assertTrue(fm.engine.game.Assistant.live(e).size <= 5)
        val rep = md.finish()
        assertTrue(f.played)
        assertTrue(rep.lines.all { it.isNotEmpty() })
        assertTrue(rep.motmId >= 0)
        assertNotNull(w.pendingPress)
        md.talk(TeamTalk.Stage.FULL, TeamTalk.Tone.CALM)
        c.advanceDay()
    }
}
