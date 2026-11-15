package fm.engine.game

import fm.core.*
import fm.engine.competition.CompetitionEngine
import fm.engine.match.*
import fm.engine.world.WorldGenerator

/**
 * Reserve / U21 squads and a weekly reserve league for the top-flight clubs of the playable
 * nation. Reserve minutes count (at a discount) towards development and playing-time happiness.
 */
object Reserves {
    const val FIRST_TEAM_SIZE = 24
    const val RESERVE_LEAGUE = WorldGenerator.IDN_1

    fun firstTeam(world: World, clubId: Long): List<Player> = world.squad(clubId).filter { !it.reserve }
    fun reserves(world: World, clubId: Long): List<Player> = world.squad(clubId).filter { it.reserve }

    /** AI rule (and the user's default): the best senior players form the first team, youngsters fill the reserves. */
    fun autoAssign(world: World, club: Club) {
        val squad = world.squad(club.id).sortedByDescending { it.ca + (if (it.position == Position.GK) 5 else 0) }
        val first = mutableSetOf<Long>()
        // Keep at least 3 goalkeepers in the first team where available.
        squad.filter { it.position == Position.GK }.take(3).forEach { first += it.id }
        for (p in squad) { if (first.size >= FIRST_TEAM_SIZE) break; first += p.id }
        for (p in squad) p.reserve = p.id !in first
    }

    /** Weekly (Mondays): AI clubs re-balance squads; the user's first team is protected from being too small. */
    fun weekly(world: World) {
        for (club in world.clubs.values) {
            if (club.id == world.manager.clubId) {
                // Never leave the user with fewer than 16 fit first-teamers: promote the best reserves.
                val fit = firstTeam(world, club.id).count { it.available }
                if (fit < 16) reserves(world, club.id).sortedByDescending { it.ca }.take(16 - fit).forEach { it.reserve = false }
                // Saves from before reserves existed: split a large squad once.
                if (world.squad(club.id).none { it.reserve } && world.squad(club.id).size > FIRST_TEAM_SIZE + 4) autoAssign(world, club)
            } else autoAssign(world, club)
        }
        playRound(world)
    }

    private fun schedule(world: World): List<List<Pair<Long, Long>>> {
        val teams = world.clubs.values.filter { it.leagueId == RESERVE_LEAGUE }.map { it.id }.sorted()
        val rng = Rng.stream(world.seed, "reservefix", world.season.toLong())
        return CompetitionEngine.roundRobin(rng.shuffled(teams), 2)
    }

    private fun ensureTable(world: World) {
        val teams = world.clubs.values.filter { it.leagueId == RESERVE_LEAGUE }.map { it.id }.toSet()
        if (world.reserveTable.map { it.clubId }.toSet() != teams) {
            world.reserveTable.clear(); teams.sorted().forEach { world.reserveTable.add(TableRow(it)) }; world.reserveRound = 0
        }
    }

    private fun sheet(world: World, club: Club): TeamSheet? {
        // Reserves plus fringe first-team players (outside the top 16) who need minutes.
        val fringe = firstTeam(world, club.id).filter { it.available }.sortedByDescending { it.ca }.drop(16)
        val pool = (reserves(world, club.id).filter { it.available } + fringe).distinctBy { it.id }
        if (pool.size < 9) return null
        val formation = Formation.F442
        val used = HashSet<Long>()
        val starters = formation.slots.mapNotNull { slot ->
            pool.filter { it.id !in used }.maxByOrNull { fm.engine.ability.Ability.effective(it, slot.position) }?.also { used += it.id }?.let { it to slot }
        }
        return TeamSheet(club.id, club.name, club.primaryColor, club.secondaryColor, starters, emptyList(), TeamInstructions(), formation, 0, 0.7)
    }

    private fun playRound(world: World) {
        val month = Cal.month(world.today)
        if (month == 6 || month == 7) return // off-season
        ensureTable(world)
        val rounds = schedule(world)
        if (world.reserveRound >= rounds.size) return
        for ((h, a) in rounds[world.reserveRound]) {
            val hc = world.club(h); val ac = world.club(a)
            val hs = sheet(world, hc); val aSheet = sheet(world, ac)
            val (hg, ag) = if (hs == null || aSheet == null) (if (hs == null) 0 to 3 else 3 to 0) else {
                val r = QuickSim.play(MatchSetup(hs, aSheet, Rng.stream(world.seed, "reserve", world.season.toLong(), world.reserveRound.toLong(), h).nextLong()), Detail.LIGHT)
                for (m in r.players.values) world.players[m.playerId]?.let { p ->
                    p.reserveApps++; p.reserveMinutes += 90; p.reserveGoals += m.goals
                }
                r.homeGoals to r.awayGoals
            }
            val hr = world.reserveTable.first { it.clubId == h }; val ar = world.reserveTable.first { it.clubId == a }
            hr.played++; ar.played++; hr.gf += hg; hr.ga += ag; ar.gf += ag; ar.ga += hg
            when { hg > ag -> { hr.won++; ar.lost++; hr.points += 3 }; hg < ag -> { ar.won++; hr.lost++; ar.points += 3 }; else -> { hr.drawn++; ar.drawn++; hr.points++; ar.points++ } }
        }
        world.reserveRound++
    }

    fun table(world: World): List<TableRow> = world.reserveTable.sortedWith(compareByDescending<TableRow> { it.points }.thenByDescending { it.gd }.thenByDescending { it.gf }.thenBy { it.clubId })

    /** Season end: record the champion and reset reserve stats and table. */
    fun endSeason(world: World) {
        table(world).firstOrNull()?.takeIf { it.played > 0 }?.let { world.reserveChampions.add("${world.season}|${world.club(it.clubId).name}") }
        while (world.reserveChampions.size > 60) world.reserveChampions.removeAt(0)
        world.reserveTable.clear(); world.reserveRound = 0
        for (p in world.players.values) { p.reserveApps = 0; p.reserveMinutes = 0; p.reserveGoals = 0 }
    }
}
