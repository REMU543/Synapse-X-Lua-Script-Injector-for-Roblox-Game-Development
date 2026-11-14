package fm.engine.game

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.ai.AiTactics
import fm.engine.ai.Board
import fm.engine.competition.CompetitionEngine
import fm.engine.development.Development
import fm.engine.finance.Finance
import fm.engine.market.Transfers
import fm.engine.match.*
import fm.engine.world.Factory
import fm.engine.world.WorldGenerator

/**
 * The game loop and the single entry point the UI talks to.
 *
 * The world advances one day at a time through a fixed, ordered pipeline (order is part of
 * determinism): finances → matches → recovery → weekly training → market → calendar events.
 * The user's own match must be played (watched or instant) before the day can complete.
 */
class Career(val world: World) {

    data class DayReport(val day: Int, val matchesPlayed: Int, val userMatchPending: Boolean, val seasonEnded: Boolean, val newMessages: Int)

    companion object {
        fun newCareer(seed: Long, profile: ManagerProfile, clubId: Long?): Career {
            val world = WorldGenerator.generate(seed, profile)
            val c = Career(world)
            Board.setExpectations(world)
            if (clubId != null && clubId in world.clubs) c.takeOverClub(clubId)
            else {
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.WELCOME, "welcome_unemployed"))
                Board.generateJobOffers(world)
            }
            return c
        }

        /** Club choices for a new career, ordered by league tier then reputation. */
        fun clubChoices(world: World): List<Club> =
            world.clubs.values.sortedWith(compareBy<Club> { world.comp(it.leagueId).tier }.thenByDescending { it.reputation })
    }

    val lang get() = world.settings.lang

    private fun takeOverClub(clubId: Long) {
        val club = world.club(clubId)
        world.staff[club.managerId]?.let { old -> old.clubId = -1; old.contract = null; club.staffIds.remove(old.id) }
        club.managerId = -1
        world.manager.clubId = clubId
        club.boardConfidence = 65
        world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.WELCOME, "welcome",
            listOf(club.name, club.expectedPosition.toString(), world.manager.name)))
    }

    fun acceptJob(clubId: Long): Boolean {
        val ok = Board.acceptJob(world, clubId)
        if (ok) world.manager.history.add(CareerEntry(world.season, clubId, world.club(clubId).name, 0, "appointed"))
        return ok
    }

    // ------------------------------------------------------------------ fixtures

    fun fixturesOn(day: Int): List<Fixture> = world.compSeasons.values.flatMap { cs -> cs.fixtures.filter { it.day == day && !it.played } }

    fun userFixtureToday(): Fixture? {
        val uid = world.manager.clubId
        if (uid < 0) return null
        return fixturesOn(world.today).firstOrNull { it.homeId == uid || it.awayId == uid }
    }

    fun nextUserFixture(): Fixture? {
        val uid = world.manager.clubId
        if (uid < 0) return null
        return world.compSeasons.values.flatMap { it.fixtures }.filter { !it.played && (it.homeId == uid || it.awayId == uid) && it.day >= world.today }
            .minByOrNull { it.day }
    }

    fun userFixtures(): List<Fixture> {
        val uid = world.manager.clubId
        return world.compSeasons.values.flatMap { it.fixtures }.filter { it.homeId == uid || it.awayId == uid }.sortedBy { it.day }
    }

    // ------------------------------------------------------------------ the day pipeline

    /**
     * Advance one day. If the user has a match today that has not been played, nothing happens
     * and `userMatchPending` is true (the UI must call [prepareUserMatch] or [playUserMatchInstant]).
     */
    fun advanceDay(): DayReport {
        val pending = userFixtureToday()
        if (pending != null) return DayReport(world.today, 0, true, false, 0)
        val inboxBefore = world.inbox.size
        val today = world.today
        val dom = Cal.dayOfMonth(today)
        val month = Cal.month(today)
        val dow = Cal.dayOfWeek(today)

        // 1. Finances on the 1st of the month
        if (dom == 1) {
            for (c in world.clubs.values) Finance.monthly(world, c)
            Board.monthlyReview(world)
            for (p in world.players.values) Factory.refreshValue(world, p)
            if (world.manager.clubId >= 0) world.userClub?.let {
                world.inbox.add(InboxMessage(world.newId(), today, MsgType.FINANCE, "finance_month", listOf(Money.format(it.balance, lang))))
            }
        }
        // 2. Matches
        var played = 0
        for (f in fixturesOn(today).sortedBy { it.id }) { playAiFixture(f); played++ }
        // 3. Daily recovery
        Development.daily(world)
        // 4. Weekly training (Mondays)
        if (dow == 1) {
            Development.weekly(world)
            Development.trainingInjuries(world)
            scoutingWeek()
            if (world.manager.clubId >= 0) world.userTacticFamiliarity = (world.userTacticFamiliarity + 1).coerceAtMost(100)
        }
        // 5. Market
        if (Transfers.windowOpen(world)) Transfers.aiDay(world)
        // 6. Calendar events
        if (month == 1 && dom == 15) { Transfers.aiRenewals(world); Transfers.warnUserContracts(world) }
        if (month == 6 && dom == 1) Transfers.aiRenewals(world)
        if (month == 3 && dom == 15) Development.youthIntake(world)
        if (world.manager.clubId < 0 && dom == 1 && world.manager.jobOffers.isEmpty()) Board.generateJobOffers(world)
        var seasonEnded = false
        if (month == 6 && dom == 30) { endSeason(); seasonEnded = true }
        world.today++
        pruneMessages()
        return DayReport(today, played, false, seasonEnded, world.inbox.size - inboxBefore)
    }

    /** Advance until the user's next match day, an important message, or [maxDays]. */
    fun continueGame(maxDays: Int = 31): DayReport {
        var last = DayReport(world.today, 0, false, false, 0)
        for (i in 0 until maxDays) {
            last = advanceDay()
            if (last.userMatchPending || last.seasonEnded) return last
            if (userFixtureToday() != null) return last.copy(userMatchPending = true)
            val important = world.inbox.takeLast(last.newMessages).any { it.type in setOf(MsgType.JOB, MsgType.BOARD, MsgType.TRANSFER, MsgType.YOUTH) }
            if (important) return last
        }
        return last
    }

    // ------------------------------------------------------------------ matches

    private fun detailFor(f: Fixture): Detail {
        val home = world.club(f.homeId)
        return if (home.nationId in world.settings.detailedNations || world.comp(f.compId).type != CompType.LEAGUE) Detail.REDUCED else Detail.LIGHT
    }

    private fun maxSubs(f: Fixture) = world.comp(f.compId).league?.maxSubs ?: 5

    fun setupFor(f: Fixture): MatchSetup {
        val home = world.club(f.homeId); val away = world.club(f.awayId)
        val uid = world.manager.clubId
        val ht = if (home.id == uid) home.tactic else AiTactics.forMatch(world, home, away, true)
        val at = if (away.id == uid) away.tactic else AiTactics.forMatch(world, away, home, false)
        val famH = if (home.id == uid) world.userTacticFamiliarity / 100.0 else 0.8
        val famA = if (away.id == uid) world.userTacticFamiliarity / 100.0 else 0.8
        val hs = Lineups.pick(world, home, ht, maxSubs(f), famH)
        val aSheet = Lineups.pick(world, away, at, maxSubs(f), famA)
        val comp = world.comp(f.compId)
        return MatchSetup(hs, aSheet, seed = Rng.stream(world.seed, "match", f.id).nextLong(),
            needsWinner = CompetitionEngine.needsWinner(world, f), firstLeg = CompetitionEngine.firstLegOffset(world, f),
            neutral = comp.type != CompType.LEAGUE && comp.cup?.singleLegFinal == true && f.leg == 0 && isFinal(f),
            userTeam = when (uid) { home.id -> 0; away.id -> 1; else -> -1 })
    }

    private fun isFinal(f: Fixture): Boolean {
        val cs = world.compSeasons[f.compId] ?: return false
        return CompetitionEngine.cupRoundCount(cs.teams.size) == f.round
    }

    private fun playAiFixture(f: Fixture) {
        val setup = setupFor(f)
        val res = QuickSim.play(setup, detailFor(f))
        apply(f, setup, res)
    }

    /** Creates the live 2D engine for today's user match. */
    fun prepareUserMatch(): MatchEngine2D? {
        val f = userFixtureToday() ?: return null
        return MatchEngine2D(setupFor(f))
    }

    /** Applies a finished (or forcibly completed) live match. */
    fun completeUserMatch(engine: MatchEngine2D) {
        val f = userFixtureToday() ?: return
        while (engine.step()) { /* finish remaining time */ }
        apply(f, engine.setup, engine.result())
    }

    /** Simulates the user's match with the full engine at instant speed. */
    fun playUserMatchInstant(): MatchResult? {
        val f = userFixtureToday() ?: return null
        val setup = setupFor(f)
        val r = MatchEngine2D(setup).runToEnd()
        apply(f, setup, r)
        return r
    }

    private fun apply(f: Fixture, setup: MatchSetup, r: MatchResult) {
        val home = world.club(f.homeId); val away = world.club(f.awayId)
        val comp = world.comp(f.compId)
        f.played = true
        f.homeGoals = r.homeGoals; f.awayGoals = r.awayGoals
        f.extraTime = r.extraTime; f.penHome = r.penHome; f.penAway = r.penAway
        f.detail = r.detail
        f.winnerId = when (r.winner) { 0 -> home.id; 1 -> away.id; else -> -1 }
        f.stats = r.stats.flatMap { it.toArray().toList() }.toIntArray()
        val isUser = setup.userTeam >= 0
        if (isUser || r.events.size < 40) {
            f.events = r.events.filter { it.type in setOf(EvType.GOAL, EvType.PEN_GOAL, EvType.YELLOW, EvType.RED, EvType.SUB, EvType.INJURY, EvType.PEN_MISS) }
                .map { "${it.minute}|${it.type.name}|${it.team}|${it.playerId}|${it.otherId}" }
        }
        // Attendance & matchday income
        val rng = Rng.stream(world.seed, "gate", f.id)
        val importance = if (comp.type == CompType.LEAGUE) 1.0 else 1.1
        f.attendance = Finance.attendance(world, home, away, rng, importance)
        Finance.income(home, Finance.MATCHDAY, Finance.matchdayIncome(world, home, f.attendance))

        when (comp.type) {
            CompType.LEAGUE -> CompetitionEngine.recordLeagueResult(world, f)
            else -> {
                if (f.winnerId < 0 && CompetitionEngine.needsWinner(world, f)) f.winnerId = home.id // safety: never stall a cup
                CompetitionEngine.recordCupResult(world, f)
                comp.cup?.participationPrize?.let { Finance.income(home, Finance.PRIZE, it / 2); Finance.income(away, Finance.PRIZE, it / 2) }
                val cs = world.compSeasons[comp.id]
                if (cs != null && cs.finished && cs.winnerId >= 0) {
                    world.clubs[cs.winnerId]?.let { w ->
                        Finance.income(w, Finance.PRIZE, comp.cup?.winnerPrize ?: 0)
                        w.honours.add("${world.season}:${comp.shortName}")
                        if (w.id == world.manager.clubId) world.manager.achievements.add("${world.season} ${comp.name(lang)}")
                        world.news.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "champion", listOf(w.name, comp.name(lang)), ref = w.id))
                    }
                }
            }
        }
        // Players
        val sheets = arrayOf(setup.home, setup.away)
        for ((t, sheet) in sheets.withIndex()) {
            val club = if (t == 0) home else away
            val gf = if (t == 0) r.homeGoals else r.awayGoals
            val ga = if (t == 0) r.awayGoals else r.homeGoals
            val played = r.players.values.filter { it.team == t }.map { it.playerId }.toSet()
            for (pid in played) {
                val p = world.players[pid] ?: continue
                val m = r.players.getValue(pid)
                val s = p.season
                if (m.minutes > 0 || m.started) {
                    s.apps++; s.minutes += m.minutes; s.goals += m.goals; s.assists += m.assists
                    s.ratingSum += Math.round(m.rating * 100).toInt()
                    if (ga == 0 && (p.position == Position.GK || p.position.line == Line.DEF) && m.minutes >= 60) s.cleanSheets++
                }
                s.yellow += m.yellow
                if (m.red) { s.red++; p.suspended = maxOf(p.suspended, if (m.yellow >= 2) 1 else 3) }
                else if (m.yellow > 0 && s.yellow % 5 == 0) p.suspended = maxOf(p.suspended, 1)
                p.condition = (m.energy * 100).toInt().coerceIn(20, 100)
                if (m.injuryDays > 0) {
                    p.injuryDays = m.injuryDays; p.injuryName = m.injuryName
                    if (club.id == world.manager.clubId) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.INJURY, "injury",
                        listOf(p.name, p.injuryName, p.injuryDays.toString()), ref = p.id))
                }
                p.morale = (p.morale + when { gf > ga -> 4; gf == ga -> 0; else -> -4 } + (if (m.rating >= 7.5) 2 else 0)).coerceIn(5, 100)
                p.benchWeeks = 0
            }
            // Suspensions served by players who did not play; benched key players get unhappy.
            for (p in world.squad(club.id)) {
                if (p.id in played) continue
                if (p.suspended > 0) p.suspended--
                if (p.contract?.squadStatus == SquadStatus.KEY || p.contract?.squadStatus == SquadStatus.FIRST_TEAM) {
                    p.benchWeeks++
                    if (p.benchWeeks > 3) p.morale = (p.morale - 2).coerceAtLeast(5)
                }
            }
            Board.afterMatch(world, club, gf, ga, if (t == 0) away else home)
            if (club.id == world.manager.clubId) {
                val m = world.manager
                m.matches++
                when { gf > ga -> m.wins++; gf == ga -> m.draws++; else -> m.losses++ }
                val opp = if (t == 0) away.name else home.name
                val key = when { gf > ga -> "result_win"; gf == ga -> "result_draw"; else -> "result_loss" }
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.MATCH_RESULT, key, listOf(gf.toString(), ga.toString(), opp), ref = f.id))
            }
        }
    }

    // ------------------------------------------------------------------ season rollover

    private fun endSeason() {
        val lang = lang
        val champions = LinkedHashMap<Int, String>()
        // Prize money and honours
        for (comp in world.competitions.filter { it.type == CompType.LEAGUE }) {
            val cs = world.compSeasons[comp.id] ?: continue
            val table = CompetitionEngine.sortedTable(world, cs)
            val rules = comp.league!!
            table.forEachIndexed { i, row ->
                val club = world.club(row.clubId)
                val prize = rules.championPrize * (table.size - i) / table.size
                Finance.income(club, Finance.PRIZE, prize)
            }
            table.firstOrNull()?.let { row ->
                val w = world.club(row.clubId)
                w.honours.add("${world.season}:${comp.shortName}")
                champions[comp.id] = w.name
                if (w.id == world.manager.clubId) world.manager.achievements.add("${world.season} ${comp.name(lang)}")
                world.news.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "champion", listOf(w.name, comp.name(lang)), ref = w.id))
            }
        }
        for (comp in world.competitions.filter { it.type != CompType.LEAGUE }) {
            world.compSeasons[comp.id]?.takeIf { it.finished && it.winnerId >= 0 }?.let { champions[comp.id] = world.club(it.winnerId).name }
        }
        val user = world.userClub
        val userPos = user?.let { CompetitionEngine.position(world, it.id) } ?: 0
        val top = world.players.values.maxByOrNull { it.season.goals }
        world.seasonHistory.add(SeasonSummary(world.season, champions, user?.name ?: "-", userPos, top?.let { "${it.name} (${it.season.goals})" } ?: "-"))
        if (user != null) {
            world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.SEASON, "season_end", listOf("${world.season}/${(world.season + 1) % 100}", user.name, userPos.toString())))
            world.manager.history.add(CareerEntry(world.season, user.id, user.name, userPos, world.comp(user.leagueId).shortName))
        }
        // Board & reputation
        Board.seasonReview(world)
        // Promotion / relegation
        val moves = CompetitionEngine.applyPromotionRelegation(world)
        for (m in moves) {
            val club = world.club(m.clubId)
            val key = if (m.promoted) "promoted" else "relegated"
            world.news.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, key, listOf(club.name, world.comp(m.to).name(lang)), ref = club.id))
            if (club.id == world.manager.clubId) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.SEASON, key, listOf(club.name, world.comp(m.to).name(lang))))
            club.reputation = (club.reputation + if (m.promoted) 4 else -4).coerceIn(1, 100)
        }
        // Player season records
        for (p in world.players.values) {
            val club = world.clubs[p.playingFor]
            if (p.season.apps > 0 || club != null) {
                p.history.add(SeasonRecord(world.season, p.playingFor, club?.name ?: "-", p.season.apps, p.season.goals, p.season.assists,
                    Math.round(p.season.avgRating * 100) / 100.0, p.ca))
                while (p.history.size > 25) p.history.removeAt(0)
            }
            p.season = SeasonStats()
            p.suspended = 0
        }
        Transfers.endLoans(world)
        Transfers.expireContracts(world)
        Development.retirements(world)
        Development.staffTurnover(world)
        Transfers.ensureMinimumSquads(world)
        // Every club must still have a manager.
        for (club in world.clubs.values) if (club.managerId < 0 && club.id != world.manager.clubId) Board.appointManager(world, club, Rng.stream(world.seed, "appoint", club.id, world.season.toLong()))
        for (c in world.clubs.values) Finance.closeSeason(world, c)
        // Keep history bounded
        while (world.transfers.size > 1500) world.transfers.removeAt(0)
        while (world.archive.size > 8000) world.archive.removeAt(0)
        // New season
        val previous = LinkedHashMap(world.compSeasons)
        world.season++
        CompetitionEngine.startSeason(world, previous)
        Board.setExpectations(world)
        for (c in world.clubs.values) Finance.setBudgets(world, c)
    }

    private fun pruneMessages() {
        while (world.inbox.size > 300) world.inbox.removeAt(0)
        while (world.news.size > 300) world.news.removeAt(0)
    }

    // ------------------------------------------------------------------ scouting

    /** Knowledge (0–100) the user's club has about a player. */
    fun knowledge(p: Player): Int {
        val uid = world.manager.clubId
        if (p.playingFor == uid || p.clubId == uid) return 100
        val k = world.manager.knowledge[p.id] ?: 0
        val sameLeague = world.clubs[p.playingFor]?.leagueId == world.userClub?.leagueId
        return maxOf(k, if (sameLeague) 55 else if (p.nationId == world.manager.nationId) 30 else 10)
    }

    /** Displayed attribute range for the user's knowledge level. */
    fun attrRange(p: Player, a: Attr): IntRange {
        val v = p.attrInt(a)
        val k = knowledge(p)
        if (k >= 100) return v..v
        val w = ((100 - k) / 100.0 * 6).toInt()
        val rng = Rng.stream(world.seed, "attrmask", p.id, a.ordinal.toLong())
        val lo = (v - rng.range(0, w)).coerceAtLeast(1)
        return lo..(lo + w).coerceAtMost(20)
    }

    /** Scout's potential estimate (stars 0.5–5), biased by judging ability when knowledge is low. */
    fun potentialStars(p: Player): Double {
        val ref = world.userClub?.let { c -> world.squad(c.id).map { it.ca }.sorted().let { l -> if (l.isEmpty()) 100.0 else l[l.size * 3 / 4].toDouble() } } ?: 100.0
        val k = knowledge(p)
        val rng = Rng.stream(world.seed, "pa_est", p.id)
        val noise = (100 - k) / 100.0 * 25 * rng.gaussian()
        return Ability.stars((p.pa + noise).toInt().coerceIn(1, 200), ref)
    }

    fun assignScouts(nationId: Int) { world.manager.scoutingNation = nationId }

    private fun scoutingWeek() {
        val user = world.userClub ?: return
        val nation = world.manager.scoutingNation
        if (nation < 0) return
        val scouts = user.staffIds.mapNotNull { world.staff[it] }.filter { it.role == StaffRole.SCOUT }
        if (scouts.isEmpty()) return
        val judging = scouts.maxOf { (it.judgingAbility + it.judgingPotential) / 2.0 }
        val gain = (4 + judging * 0.8).toInt()
        val rng = Rng.stream(world.seed, "scouting", world.today.toLong())
        val pool = world.players.values.filter { (world.clubs[it.playingFor]?.nationId ?: it.nationId) == nation }
        val sample = rng.shuffled(pool.map { it.id }).take(40 + judging.toInt() * 4)
        for (id in sample) world.manager.knowledge[id] = ((world.manager.knowledge[id] ?: 0) + gain).coerceAtMost(95)
        if (world.manager.knowledge.size > 6000) {
            val drop = world.manager.knowledge.keys.filter { it !in world.players }.take(2000)
            drop.forEach { world.manager.knowledge.remove(it) }
        }
        if (Cal.dayOfMonth(world.today) <= 7) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.SCOUT, "scout_report",
            listOf(sample.size.toString(), world.nation(nation).name(lang))))
    }

    // ------------------------------------------------------------------ user management actions

    fun setTactic(t: Tactic) {
        val club = world.userClub ?: return
        if (t.formation.name != club.tactic.formation.name) world.userTacticFamiliarity = (world.userTacticFamiliarity - 15).coerceAtLeast(20)
        club.tactic = t
    }

    fun setTraining(focus: TrainingFocus, intensity: Int) {
        val club = world.userClub ?: return
        club.trainingFocus = focus
        club.trainingIntensity = intensity.coerceIn(1, 3)
    }

    fun toggleTransferList(playerId: Long) {
        val p = world.players[playerId] ?: return
        if (p.clubId == world.manager.clubId) p.transferListed = !p.transferListed
    }

    fun releasePlayer(playerId: Long) {
        val p = world.players[playerId] ?: return
        if (p.clubId == world.manager.clubId) Transfers.release(world, p)
    }

    fun signFreeAgent(playerId: Long, wage: Long, years: Int): Boolean {
        val user = world.userClub ?: return false
        val p = world.players[playerId] ?: return false
        if (p.clubId >= 0) return false
        if (wage < Transfers.wageDemand(world, p, user)) return false
        if (!Transfers.playerAcceptsMove(world, p, null, user, wage)) return false
        Transfers.completeTransfer(world, p, null, user, 0, wage, years.coerceIn(1, 5))
        return true
    }

    fun offer(playerId: Long, fee: Long, wage: Long, years: Int) = Transfers.userOffer(world, playerId, fee, wage, years)

    fun renew(playerId: Long, wage: Long, years: Int) = Transfers.userRenew(world, playerId, wage, years)

    fun leagueTable(compId: Int): List<TableRow> = world.compSeasons[compId]?.let { CompetitionEngine.sortedTable(world, it) } ?: emptyList()

    fun message(m: InboxMessage) = I18n.t(lang, m.key, m.args)
}
