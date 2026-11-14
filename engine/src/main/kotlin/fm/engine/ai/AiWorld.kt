package fm.engine.ai

import fm.core.*
import fm.engine.competition.CompetitionEngine
import fm.engine.world.WorldGenerator

/** Boards, manager careers (sackings & appointments) and match-day tactical choices of AI managers. */
object Board {
    /** Expected league finish from the reputation ranking inside the league. */
    fun setExpectations(world: World) {
        for (comp in world.competitions.filter { it.type == CompType.LEAGUE }) {
            val clubs = world.clubs.values.filter { it.leagueId == comp.id }.sortedByDescending { it.reputation }
            clubs.forEachIndexed { i, c -> c.expectedPosition = i + 1 }
        }
    }

    fun afterMatch(world: World, club: Club, goalsFor: Int, goalsAgainst: Int, opponent: Club) {
        val diff = opponent.reputation - club.reputation
        val delta = when {
            goalsFor > goalsAgainst -> 2 + (if (diff > 10) 2 else 0)
            goalsFor == goalsAgainst -> if (diff > 5) 1 else if (diff < -10) -1 else 0
            else -> -2 - (if (diff < -10) 2 else 0)
        }
        club.boardConfidence = (club.boardConfidence + delta).coerceIn(0, 100)
        club.form.add(if (goalsFor > goalsAgainst) 'W' else if (goalsFor == goalsAgainst) 'D' else 'L')
        while (club.form.size > 10) club.form.removeAt(0)
    }

    /** Monthly review: AI boards sack managers with very low confidence; the user gets warnings. */
    fun monthlyReview(world: World) {
        val month = Cal.month(world.today)
        if (month == 7 || month == 8) return
        for (club in world.clubs.values) {
            val pos = CompetitionEngine.position(world, club.id)
            val gap = pos - club.expectedPosition
            if (gap > 4) club.boardConfidence = (club.boardConfidence - (gap - 4)).coerceAtLeast(0)
            if (gap < -2) club.boardConfidence = (club.boardConfidence + 2).coerceAtMost(100)
            if (club.id == world.manager.clubId) {
                if (club.boardConfidence < 30) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.BOARD, "board_warning", listOf(club.boardConfidence.toString())))
                continue
            }
            val rng = Rng.stream(world.seed, "sack", club.id, world.today.toLong())
            if (club.boardConfidence < 18 && rng.chance(0.6)) sackAiManager(world, club, rng)
        }
    }

    private fun sackAiManager(world: World, club: Club, rng: Rng) {
        val old = world.staff[club.managerId]
        if (old != null) {
            old.clubId = -1; old.contract = null
            old.reputation = (old.reputation - 5).coerceAtLeast(1)
            old.careerLog.add("${world.season}: dipecat ${club.name}")
            club.staffIds.remove(old.id)
            world.news.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "manager_sacked", listOf(old.name, club.name), ref = club.id))
        }
        club.managerId = -1
        // Offer the job to the unemployed user if suitable; otherwise appoint from the pool.
        if (world.manager.clubId < 0 && world.manager.reputation >= club.reputation - 25 && club.id !in world.manager.jobOffers) {
            world.manager.jobOffers.add(club.id)
            world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.JOB, "job_offer", listOf(club.name), ref = club.id))
        }
        appointManager(world, club, rng)
        club.boardConfidence = 60
    }

    fun appointManager(world: World, club: Club, rng: Rng) {
        val pool = world.staff.values.filter { it.clubId < 0 && it.role == StaffRole.MANAGER && !it.retired }
            .sortedByDescending { -kotlin.math.abs(it.reputation - club.reputation) + it.quality() * 2 + rng.nextDouble() * 5 }
        val m = pool.firstOrNull() ?: return
        m.clubId = club.id
        m.contract = Contract(club.id, world.today, world.season + 2, WorldGenerator.staffWage(world.nation(club.nationId), StaffRole.MANAGER, m.quality()))
        m.careerLog.add("${world.season}: ditunjuk ${club.name}")
        club.staffIds.add(m.id)
        club.managerId = m.id
        club.tactic = Tactic(formation = Formation.byName(m.style.preferredFormation), instructions = WorldGenerator.styleInstructions(m.style))
        world.news.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "manager_hired", listOf(m.name, club.name), ref = club.id))
    }

    /** End of season: reputation drift and the user's evaluation. Returns true if the user was sacked. */
    fun seasonReview(world: World): Boolean {
        for (club in world.clubs.values) {
            val pos = CompetitionEngine.position(world, club.id)
            val gap = club.expectedPosition - pos
            val league = world.comp(club.leagueId)
            val tierBase = (league.reputation + 10).coerceIn(10, 90)
            var rep = club.reputation + gap * 0.6 + (tierBase - club.reputation) * 0.05
            if (club.honours.any { it.startsWith("${world.season}:") }) rep += 3
            club.reputation = Math.round(rep).toInt().coerceIn(1, 100)
            club.fanBase = (club.fanBase * (1.0 + gap * 0.01)).toInt().coerceAtLeast(500)
        }
        val user = world.userClub ?: return false
        val pos = CompetitionEngine.position(world, user.id)
        val gap = pos - user.expectedPosition
        user.boardConfidence = (user.boardConfidence - gap * 4).coerceIn(0, 100)
        world.manager.reputation = (world.manager.reputation - gap).coerceIn(1, 100)
        if (gap <= -2) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.BOARD, "board_praise"))
        if (user.boardConfidence < 12) {
            sackUser(world, user)
            return true
        }
        return false
    }

    fun sackUser(world: World, club: Club) {
        world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.BOARD, "board_sacked", listOf(club.name)))
        world.manager.history.add(CareerEntry(world.season, club.id, club.name, CompetitionEngine.position(world, club.id), "sacked"))
        world.manager.sackings++
        world.manager.clubId = -1
        world.manager.reputation = (world.manager.reputation - 8).coerceAtLeast(1)
        club.boardConfidence = 60
        appointManager(world, club, Rng.stream(world.seed, "replaceuser", world.today.toLong()))
        generateJobOffers(world)
    }

    /** Clubs whose level matches the user's reputation consider him. */
    fun generateJobOffers(world: World) {
        if (world.manager.clubId >= 0) return
        val rng = Rng.stream(world.seed, "joboffers", world.today.toLong())
        val rep = world.manager.reputation
        val candidates = world.clubs.values.filter { it.nationId == world.manager.nationId || rng.chance(0.2) }
            .filter { it.reputation <= rep + 15 && it.reputation >= rep - 30 }
            .sortedByDescending { it.reputation }
        for (c in rng.shuffled(candidates).take(3)) {
            if (c.id !in world.manager.jobOffers) {
                world.manager.jobOffers.add(c.id)
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.JOB, "job_offer", listOf(c.name), ref = c.id))
            }
        }
    }

    /** The user accepts a job offer: the club's current manager steps aside. */
    fun acceptJob(world: World, clubId: Long): Boolean {
        if (world.manager.clubId >= 0 || clubId !in world.manager.jobOffers) return false
        val club = world.clubs[clubId] ?: return false
        world.staff[club.managerId]?.let { old ->
            old.clubId = -1; old.contract = null; club.staffIds.remove(old.id)
        }
        club.managerId = -1
        world.manager.clubId = club.id
        world.manager.jobOffers.clear()
        club.boardConfidence = 60
        world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.JOB, "job_hired", listOf(club.name), ref = club.id))
        return true
    }
}

object AiTactics {
    /** AI managers adapt mentality to the opponent; their philosophy decides the base shape. */
    fun forMatch(world: World, club: Club, opponent: Club, home: Boolean): Tactic {
        val m = world.staff[club.managerId] ?: return club.tactic
        val diff = club.reputation - opponent.reputation + (if (home) 5 else -5)
        val base = club.tactic.instructions
        val mentality = when {
            diff > 15 -> if (m.style.attacking > 0) Mentality.ATTACKING else Mentality.POSITIVE
            diff < -15 -> if (m.style.attacking < 0.3) Mentality.DEFENSIVE else Mentality.BALANCED
            else -> base.mentality
        }
        return club.tactic.copy(instructions = base.copy(mentality = mentality), lineup = emptyList())
    }
}
