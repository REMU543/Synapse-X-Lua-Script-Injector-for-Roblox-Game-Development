package fm.engine.competition

import fm.core.*

/**
 * Data-driven competition framework: leagues (round robin, configurable points and
 * tiebreakers, promotion/relegation links) and knockout cups (single or two-legged,
 * byes, extra time and penalties). Rules live in [Competition] data, not in code.
 */
object CompetitionEngine {

    // ---------------------------------------------------------------- season setup

    fun startSeason(world: World, previous: Map<Int, CompSeason> = emptyMap()) {
        world.compSeasons.clear()
        for (comp in world.competitions) {
            val cs = when (comp.type) {
                CompType.LEAGUE -> startLeague(world, comp)
                CompType.CUP -> startCup(world, comp, entrants(world, comp))
                CompType.CONTINENTAL -> startCup(world, comp, continentalEntrants(world, comp, previous))
            }
            world.compSeasons[comp.id] = cs
        }
    }

    private fun startLeague(world: World, comp: Competition): CompSeason {
        val rules = comp.league!!
        val teams = world.clubs.values.filter { it.leagueId == comp.id }.map { it.id }.sorted().toMutableList()
        val cs = CompSeason(comp.id, world.season, teams)
        teams.forEach { cs.table.add(TableRow(it)) }
        val rng = Rng.stream(world.seed, "fixtures", world.season.toLong(), comp.id.toLong())
        val order = rng.shuffled(teams)
        val rounds = roundRobin(order, rules.rounds)
        val start = Cal.nextDow(Cal.of(world.season, 8, 8), rules.matchDow)
        rounds.forEachIndexed { r, pairs ->
            val day = start + r * 7
            for ((h, a) in pairs) cs.fixtures.add(Fixture(world.newId(), comp.id, r + 1, day, h, a))
        }
        return cs
    }

    /** Circle-method round robin. Returns rounds of (home, away) pairs. */
    fun roundRobin(teams: List<Long>, cycles: Int): List<List<Pair<Long, Long>>> {
        val list = teams.toMutableList()
        if (list.size % 2 == 1) list.add(-1L)
        val n = list.size
        val firstCycle = mutableListOf<List<Pair<Long, Long>>>()
        val arr = list.toMutableList()
        for (r in 0 until n - 1) {
            val pairs = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until n / 2) {
                val a = arr[i]; val b = arr[n - 1 - i]
                if (a == -1L || b == -1L) continue
                // Alternate home advantage so nobody plays too many in a row at home.
                pairs.add(if ((r + i) % 2 == 0) a to b else b to a)
            }
            firstCycle.add(pairs)
            // rotate all but the first
            val last = arr.removeAt(n - 1)
            arr.add(1, last)
        }
        val all = mutableListOf<List<Pair<Long, Long>>>()
        for (c in 0 until cycles) {
            for (round in firstCycle) all.add(if (c % 2 == 0) round else round.map { it.second to it.first })
        }
        return all
    }

    private fun entrants(world: World, comp: Competition): List<Long> {
        val rules = comp.cup!!
        return world.clubs.values.filter { it.leagueId in rules.entrantLeagues }.map { it.id }.sorted()
    }

    private fun continentalEntrants(world: World, comp: Competition, previous: Map<Int, CompSeason>): List<Long> {
        val out = mutableListOf<Long>()
        for (q in comp.cup!!.qualification) {
            val nationId = q[0]; val places = q[1]
            val top = world.competitions.firstOrNull { it.type == CompType.LEAGUE && it.nationId == nationId && it.tier == 1 } ?: continue
            val prev = previous[top.id]
            val ranked: List<Long> = if (prev != null && prev.finished) {
                sortedTable(world, prev).map { it.clubId }.filter { world.clubs[it]?.leagueId == top.id || true }
            } else {
                world.clubs.values.filter { it.leagueId == top.id }.sortedByDescending { it.reputation }.map { it.id }
            }
            out.addAll(ranked.filter { it in world.clubs }.take(places))
        }
        return out
    }

    private fun startCup(world: World, comp: Competition, entrants: List<Long>): CompSeason {
        val cs = CompSeason(comp.id, world.season, entrants.toMutableList())
        cs.alive.addAll(entrants)
        if (entrants.size >= 2) drawNextRound(world, comp, cs)
        else cs.finished = true
        return cs
    }

    fun cupRoundCount(entrants: Int): Int {
        var n = 1; var r = 0
        while (n < entrants) { n *= 2; r++ }
        return r
    }

    /** Date of the given leg index for a cup round. */
    private fun cupDate(world: World, comp: Competition, dateIndex: Int): Int {
        val rules = comp.cup!!
        val md = rules.roundDates.getOrNull(dateIndex) ?: rules.roundDates.last()
        val year = if (md[0] >= 7) world.season else world.season + 1
        var d = Cal.nextDow(Cal.of(year, md[0], md[1]), rules.matchDow)
        // If the list ran out, push later rounds a week apart.
        if (dateIndex >= rules.roundDates.size) d += 7 * (dateIndex - rules.roundDates.size + 1)
        return d
    }

    private fun dateIndexFor(comp: Competition, cs: CompSeason, round: Int, leg: Int): Int {
        val rules = comp.cup!!
        if (!rules.twoLegged) return round - 1
        val total = cupRoundCount(cs.teams.size)
        // Every round before the final uses two dates.
        return if (round == total && rules.singleLegFinal) (round - 1) * 2 else (round - 1) * 2 + leg
    }

    private fun drawNextRound(world: World, comp: Competition, cs: CompSeason) {
        val rules = comp.cup!!
        cs.currentRound++
        val round = cs.currentRound
        val rng = Rng.stream(world.seed, "cupdraw", world.season.toLong(), comp.id.toLong(), round.toLong())
        val alive = cs.alive.toMutableList()
        val target = Integer.highestOneBit(maxOf(1, alive.size - 1)) * 2 // next power of two ≥ size
        val byes = if (round == 1 && alive.size != target) target - alive.size else 0
        // Highest-reputation clubs receive the byes.
        val byeClubs = alive.sortedByDescending { world.clubs[it]?.reputation ?: 0 }.take(byes).toSet()
        val drawPool = rng.shuffled(alive.filter { it !in byeClubs })
        val total = cupRoundCount(cs.teams.size)
        val isFinal = round == total
        val twoLegs = rules.twoLegged && !(isFinal && rules.singleLegFinal)
        for (i in 0 until drawPool.size / 2) {
            val h = drawPool[2 * i]; val a = drawPool[2 * i + 1]
            val f1 = Fixture(world.newId(), comp.id, round, cupDate(world, comp, dateIndexFor(comp, cs, round, 0)), h, a, leg = if (twoLegs) 1 else 0)
            cs.fixtures.add(f1)
            if (twoLegs) {
                cs.fixtures.add(Fixture(world.newId(), comp.id, round, cupDate(world, comp, dateIndexFor(comp, cs, round, 1)), a, h, leg = 2, firstLegId = f1.id))
            }
        }
        // Clubs with byes simply stay alive; mark them via a bye "fixture"-less advance.
        cs.alive.clear()
        cs.alive.addAll(byeClubs)
        cs.alive.addAll(drawPool) // they remain alive until eliminated
    }

    // ---------------------------------------------------------------- results

    /** Whether this fixture must produce a winner (knockout, decided now). */
    fun needsWinner(world: World, f: Fixture): Boolean {
        val comp = world.comp(f.compId)
        if (comp.type == CompType.LEAGUE) return false
        return f.leg == 0 || f.leg == 2
    }

    /** Goals from the first leg as (forHomeOfThisFixture, forAwayOfThisFixture). */
    fun firstLegOffset(world: World, f: Fixture): Pair<Int, Int> {
        if (f.leg != 2) return 0 to 0
        val cs = world.compSeasons[f.compId] ?: return 0 to 0
        val first = cs.fixtures.firstOrNull { it.id == f.firstLegId } ?: return 0 to 0
        // In the first leg this fixture's away team was at home.
        return first.awayGoals to first.homeGoals
    }

    fun recordLeagueResult(world: World, f: Fixture) {
        val comp = world.comp(f.compId)
        val rules = comp.league ?: return
        val cs = world.compSeasons[f.compId] ?: return
        val h = cs.table.first { it.clubId == f.homeId }
        val a = cs.table.first { it.clubId == f.awayId }
        h.played++; a.played++
        h.gf += f.homeGoals; h.ga += f.awayGoals
        a.gf += f.awayGoals; a.ga += f.homeGoals
        when {
            f.homeGoals > f.awayGoals -> { h.won++; a.lost++; h.points += rules.pointsWin }
            f.homeGoals < f.awayGoals -> { a.won++; h.lost++; a.points += rules.pointsWin }
            else -> { h.drawn++; a.drawn++; h.points += rules.pointsDraw; a.points += rules.pointsDraw }
        }
        if (cs.fixtures.all { it.played }) {
            cs.finished = true
            val t = sortedTable(world, cs)
            cs.winnerId = t.first().clubId
            cs.runnerUpId = t.getOrNull(1)?.clubId ?: -1
        }
    }

    /** After a cup fixture is played: eliminate the loser and draw the next round when ready. */
    fun recordCupResult(world: World, f: Fixture) {
        val comp = world.comp(f.compId)
        val cs = world.compSeasons[f.compId] ?: return
        if (f.leg == 1) return // first legs decide nothing
        val loser = if (f.winnerId == f.homeId) f.awayId else f.homeId
        cs.alive.remove(loser)
        val roundFixtures = cs.fixtures.filter { it.round == cs.currentRound }
        if (roundFixtures.all { it.played }) {
            if (cs.alive.size <= 1) {
                cs.finished = true
                cs.winnerId = cs.alive.firstOrNull() ?: f.winnerId
                cs.runnerUpId = loser
            } else drawNextRound(world, comp, cs)
        }
    }

    // ---------------------------------------------------------------- tables

    fun sortedTable(world: World, cs: CompSeason): List<TableRow> {
        val rules = world.comp(cs.compId).league ?: return cs.table
        val byPoints = cs.table.groupBy { it.points }
        val h2h = HashMap<Long, Int>()
        if (Tiebreaker.HEAD_TO_HEAD in rules.tiebreakers) {
            for ((_, group) in byPoints) {
                if (group.size < 2) continue
                val ids = group.map { it.clubId }.toSet()
                for (f in cs.fixtures) {
                    if (!f.played || f.homeId !in ids || f.awayId !in ids) continue
                    when {
                        f.homeGoals > f.awayGoals -> h2h.merge(f.homeId, rules.pointsWin, Int::plus)
                        f.homeGoals < f.awayGoals -> h2h.merge(f.awayId, rules.pointsWin, Int::plus)
                        else -> { h2h.merge(f.homeId, rules.pointsDraw, Int::plus); h2h.merge(f.awayId, rules.pointsDraw, Int::plus) }
                    }
                }
            }
        }
        fun key(r: TableRow, t: Tiebreaker): Int = when (t) {
            Tiebreaker.POINTS -> r.points
            Tiebreaker.GOAL_DIFFERENCE -> r.gd
            Tiebreaker.GOALS_FOR -> r.gf
            Tiebreaker.HEAD_TO_HEAD -> h2h[r.clubId] ?: 0
            Tiebreaker.WINS -> r.won
        }
        val cmp = Comparator<TableRow> { x, y ->
            for (t in rules.tiebreakers) {
                val c = key(y, t).compareTo(key(x, t))
                if (c != 0) return@Comparator c
            }
            x.clubId.compareTo(y.clubId)
        }
        return cs.table.sortedWith(cmp)
    }

    fun position(world: World, clubId: Long): Int {
        val club = world.clubs[clubId] ?: return 0
        val cs = world.compSeasons[club.leagueId] ?: return 0
        return sortedTable(world, cs).indexOfFirst { it.clubId == clubId } + 1
    }

    // ---------------------------------------------------------------- season end

    data class Movement(val clubId: Long, val from: Int, val to: Int, val promoted: Boolean)

    /** Applies promotion and relegation between linked leagues. */
    fun applyPromotionRelegation(world: World): List<Movement> {
        val moves = mutableListOf<Movement>()
        for (comp in world.competitions.filter { it.type == CompType.LEAGUE }) {
            val rules = comp.league!!
            val cs = world.compSeasons[comp.id] ?: continue
            val table = sortedTable(world, cs)
            if (rules.lowerLeagueId >= 0 && rules.relegate > 0) {
                table.takeLast(rules.relegate).forEach { moves.add(Movement(it.clubId, comp.id, rules.lowerLeagueId, false)) }
            }
            if (rules.upperLeagueId >= 0 && rules.promote > 0) {
                table.take(rules.promote).forEach { moves.add(Movement(it.clubId, comp.id, rules.upperLeagueId, true)) }
            }
        }
        for (m in moves) world.clubs[m.clubId]?.leagueId = m.to
        return moves
    }
}
