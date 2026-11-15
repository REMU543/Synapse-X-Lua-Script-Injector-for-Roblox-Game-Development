package fm.engine.international

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.match.*
import fm.engine.world.Factory
import fm.engine.world.NamePools

/**
 * National teams: call-ups in international windows, friendlies, and a biennial regional
 * tournament (two groups of four, semi-finals, final). Matches use the reduced match model.
 * Simplification: club fixtures are not paused during windows; national duty costs condition.
 */
object International {
    /** International windows: (month, day) of the first match; a second match follows 3 days later. */
    private val windows = listOf(9 to 7, 10 to 12, 11 to 16, 3 to 23)
    const val SQUAD_SIZE = 23

    fun ensureTeams(world: World) {
        if (world.nationalTeams.size == world.nations.size) return
        for (n in world.nations) if (n.id !in world.nationalTeams) {
            val rng = Rng.stream(world.seed, "natmanager", n.id.toLong())
            val (fn, ln) = Factory.name(rng, NamePools.pool(n.namePool))
            world.nationalTeams[n.id] = NationalTeam(n.id, "$fn $ln".trim())
        }
    }

    fun windowStartDays(world: World): List<Int> = windows.map { (m, d) ->
        val year = if (m >= 7) world.season else world.season + 1
        Cal.nextDow(Cal.of(year, m, d), 4) // Thursdays
    }

    /** Called daily by the game loop. */
    fun daily(world: World) {
        ensureTeams(world)
        val today = world.today
        // Call-ups and friendlies a few days before each window.
        for (start in windowStartDays(world)) {
            if (today == start - 3) {
                callUps(world)
                scheduleFriendlies(world, start)
            }
        }
        // Tournament in even years, early December.
        if (world.season % 2 == 0 && today == Cal.of(world.season, 11, 25)) startTournament(world)
        for (f in world.intlFixtures.filter { it.day == today && !it.played }) play(world, f)
        if (Cal.month(today) == 6 && Cal.dayOfMonth(today) == 29) {
            // Keep the fixture list small: drop last season's played games.
            world.intlFixtures.removeAll { it.played && today - it.day > 200 }
        }
    }

    fun callUps(world: World) {
        val byNation = world.players.values.groupBy { it.nationId }
        for ((nid, team) in world.nationalTeams) {
            val pool = byNation[nid].orEmpty().filter { it.injuryDays <= 0 && it.age(world.today) >= 17 }
            val picked = mutableListOf<Player>()
            val need = mapOf(Line.GK to 3, Line.DEF to 8, Line.MID to 7, Line.ATT to 5)
            for ((line, n) in need) picked += pool.filter { it.position.line == line }.sortedByDescending { it.ca }.take(n)
            val rest = pool.filter { it !in picked }.sortedByDescending { it.ca }
            picked += rest.take((SQUAD_SIZE - picked.size).coerceAtLeast(0))
            val previous = team.squad.toSet()
            team.squad.clear(); team.squad.addAll(picked.map { it.id })
            for (p in picked) {
                p.morale = (p.morale + if (p.id in previous) 1 else 4).coerceAtMost(100)
                if (p.playingFor == world.manager.clubId && p.id !in previous) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "intl_callup",
                    listOf(p.name, world.nation(nid).name(world.settings.lang)), ref = p.id))
            }
        }
    }

    private fun scheduleFriendlies(world: World, start: Int) {
        if (world.intlFixtures.any { it.day == start && it.stage == "F" }) return
        val rng = Rng.stream(world.seed, "friendlies", start.toLong())
        val busy = world.intlFixtures.filter { !it.played && it.stage != "F" }.flatMap { listOf(it.home, it.away) }.toSet()
        for (k in 0..1) {
            val nations = rng.shuffled(world.nationalTeams.keys.filter { it !in busy }.sorted())
            for (i in 0 until nations.size / 2) world.intlFixtures.add(IntlFixture(world.newId(), start + 3 * k, nations[2 * i], nations[2 * i + 1], "F"))
        }
    }

    fun startTournament(world: World) {
        callUps(world)
        val ranked = world.nationalTeams.keys.sortedByDescending { strength(world, it) }.take(8)
        val rng = Rng.stream(world.seed, "tourdraw", world.season.toLong())
        // Seeded draw: pots of two.
        val g1 = mutableListOf<Int>(); val g2 = mutableListOf<Int>()
        for (pot in ranked.chunked(2)) { val s = rng.shuffled(pot); g1 += s[0]; if (s.size > 1) g2 += s[1] }
        val t = IntlTournament(world.season, "Piala Asia-Pasifik ${world.season}", "Asia-Pacific Cup ${world.season}", listOf(g1, g2))
        world.tournament = t
        val start = Cal.of(world.season, 12, 1)
        for ((gi, g) in t.groups.withIndex()) {
            val rounds = fm.engine.competition.CompetitionEngine.roundRobin(g.map { it.toLong() }, 1)
            rounds.forEachIndexed { r, pairs -> for ((h, a) in pairs) world.intlFixtures.add(IntlFixture(world.newId(), start + r * 3, h.toInt(), a.toInt(), "G", gi)) }
        }
        world.news.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "intl_tournament_start", listOf(t.name(world.settings.lang))))
    }

    data class GroupRow(val nation: Int, var p: Int = 0, var w: Int = 0, var d: Int = 0, var l: Int = 0, var gf: Int = 0, var ga: Int = 0) { val pts get() = w * 3 + d; val gd get() = gf - ga }

    fun groupTable(world: World, group: Int): List<GroupRow> {
        val t = world.tournament ?: return emptyList()
        val rows = t.groups[group].associateWith { GroupRow(it) }
        for (f in world.intlFixtures.filter { it.stage == "G" && it.group == group && it.played }) {
            val h = rows.getValue(f.home); val a = rows.getValue(f.away)
            h.p++; a.p++; h.gf += f.homeGoals; h.ga += f.awayGoals; a.gf += f.awayGoals; a.ga += f.homeGoals
            when { f.homeGoals > f.awayGoals -> { h.w++; a.l++ }; f.homeGoals < f.awayGoals -> { a.w++; h.l++ }; else -> { h.d++; a.d++ } }
        }
        return rows.values.sortedWith(compareByDescending<GroupRow> { it.pts }.thenByDescending { it.gd }.thenByDescending { it.gf }.thenBy { it.nation })
    }

    fun strength(world: World, nationId: Int): Double {
        val team = world.nationalTeams[nationId] ?: return 0.0
        val ps = team.squad.mapNotNull { world.players[it] }.sortedByDescending { it.ca }.take(11)
        return if (ps.isEmpty()) world.nation(nationId).reputation.toDouble() else ps.map { it.ca }.average()
    }

    /** Builds a match sheet for a national team from its call-up (best XI by position). */
    fun sheet(world: World, nationId: Int): TeamSheet {
        val team = world.nationalTeams.getValue(nationId)
        val players = team.squad.mapNotNull { world.players[it] }.filter { it.injuryDays <= 0 }.toMutableList()
        val formation = Formation.F433
        val used = HashSet<Long>()
        val starters = mutableListOf<Pair<Player, Slot>>()
        for (slot in formation.slots.sortedBy { if (it.position == Position.GK) 0 else 1 }) {
            val best = players.filter { it.id !in used }.maxByOrNull { Ability.effective(it, slot.position) * (if (slot.position == Position.GK && it.position != Position.GK) 0.2 else 1.0) } ?: continue
            used += best.id; starters += best to slot
        }
        val bench = players.filter { it.id !in used }.sortedByDescending { it.ca }.take(9)
        val n = world.nation(nationId)
        val color = listOf(0xFFD32F2F, 0xFF1976D2, 0xFF388E3C, 0xFFFBC02D, 0xFF7B1FA2, 0xFFF57C00, 0xFF0097A7, 0xFFC2185B, 0xFF303F9F, 0xFF689F38)[nationId % 10]
        return TeamSheet(-nationId.toLong(), n.nameId, color, 0xFFFFFFFF, starters, bench, TeamInstructions(), formation, 5, 0.85)
    }

    fun play(world: World, f: IntlFixture) {
        if (world.nationalTeams[f.home]?.squad.isNullOrEmpty() || world.nationalTeams[f.away]?.squad.isNullOrEmpty()) callUps(world)
        val ko = f.stage == "SF" || f.stage == "F1"
        val setup = MatchSetup(sheet(world, f.home), sheet(world, f.away), Rng.stream(world.seed, "intl", f.id).nextLong(), needsWinner = ko, neutral = f.stage != "F")
        val r = QuickSim.play(setup, Detail.REDUCED)
        f.played = true; f.homeGoals = r.homeGoals; f.awayGoals = r.awayGoals; f.penHome = r.penHome; f.penAway = r.penAway
        f.winner = when (r.winner) { 0 -> f.home; 1 -> f.away; else -> -1 }
        f.scorers = r.events.filter { it.type == EvType.GOAL || it.type == EvType.PEN_GOAL }.map { it.playerId }
        for ((t, nid) in listOf(0 to f.home, 1 to f.away)) {
            val team = world.nationalTeams.getValue(nid)
            val gf = if (t == 0) r.homeGoals else r.awayGoals; val ga = if (t == 0) r.awayGoals else r.homeGoals
            team.played++; team.goalsFor += gf; team.goalsAgainst += ga
            when { gf > ga -> team.won++; gf == ga -> team.drawn++; else -> team.lost++ }
            team.results.add("${f.day}|${if (t == 0) f.away else f.home}|$gf|$ga|${f.stage}")
            while (team.results.size > 20) team.results.removeAt(0)
        }
        // Players: caps, goals, fatigue and injuries.
        for (m in r.players.values) {
            val p = world.players[m.playerId] ?: continue
            if (m.minutes > 0) p.internationalCaps++
            p.internationalGoals += m.goals
            p.condition = (p.condition - 20).coerceAtLeast(40)
            if (m.injuryDays > 0) {
                p.injuryDays = m.injuryDays; p.injuryName = m.injuryName
                if (p.playingFor == world.manager.clubId) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.INJURY, "intl_injury",
                    listOf(p.name, world.nation(p.nationId).name(world.settings.lang), p.injuryDays.toString()), ref = p.id))
            }
        }
        if (f.stage != "F") advanceTournament(world)
    }

    private fun advanceTournament(world: World) {
        val t = world.tournament ?: return
        val tf = world.intlFixtures.filter { it.stage != "F" && Cal.year(it.day) == t.year }
        val groupDone = tf.filter { it.stage == "G" }.all { it.played }
        if (groupDone && tf.none { it.stage == "SF" }) {
            val a = groupTable(world, 0); val b = groupTable(world, 1)
            val day = tf.maxOf { it.day } + 4
            world.intlFixtures.add(IntlFixture(world.newId(), day, a[0].nation, b[1].nation, "SF"))
            world.intlFixtures.add(IntlFixture(world.newId(), day, b[0].nation, a[1].nation, "SF"))
        }
        val sfs = tf.filter { it.stage == "SF" }
        if (sfs.size == 2 && sfs.all { it.played } && tf.none { it.stage == "F1" }) {
            world.intlFixtures.add(IntlFixture(world.newId(), sfs.maxOf { it.day } + 4, sfs[0].winner, sfs[1].winner, "F1"))
        }
        val fin = tf.firstOrNull { it.stage == "F1" && it.played }
        if (fin != null && t.winner < 0) {
            t.winner = fin.winner; t.runnerUp = if (fin.winner == fin.home) fin.away else fin.home
            world.nationalTeams[t.winner]?.titles?.add(t.year)
            world.tournamentHistory.add("${t.year}|${t.winner}|${t.runnerUp}")
            world.news.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "intl_champion",
                listOf(world.nation(t.winner).name(world.settings.lang), t.name(world.settings.lang))))
        }
    }
}
