package fm.engine.game

import fm.core.*

/** Analytics derived from recorded fixture statistics (team & player data hub). */
object DataHub {
    /** Index of each value inside a team's 13-value stats block (see TeamStats.toArray). */
    private const val SHOTS = 0; private const val ON_T = 1; private const val XG = 2; private const val PASSES = 3
    private const val PASS_C = 4; private const val FOULS = 5; private const val CORNERS = 8; private const val POSS = 12

    data class TeamAverages(
        val matches: Int, val goalsFor: Double, val goalsAgainst: Double, val xgFor: Double, val xgAgainst: Double,
        val shotsFor: Double, val shotsAgainst: Double, val possession: Double, val passPct: Double, val corners: Double, val fouls: Double,
    )

    data class MatchPoint(val day: Int, val opponent: Long, val gf: Int, val ga: Int, val xgFor: Double, val xgAgainst: Double, val home: Boolean)

    fun matchPoints(world: World, clubId: Long, compId: Int? = null): List<MatchPoint> =
        world.compSeasons.values.flatMap { it.fixtures }
            .filter { it.played && (it.homeId == clubId || it.awayId == clubId) && it.stats.size >= 26 && (compId == null || it.compId == compId) }
            .sortedBy { it.day }
            .map { f ->
                val home = f.homeId == clubId
                val o = if (home) 0 else 13; val t = if (home) 13 else 0
                MatchPoint(f.day, if (home) f.awayId else f.homeId, if (home) f.homeGoals else f.awayGoals, if (home) f.awayGoals else f.homeGoals,
                    f.stats[o + XG] / 100.0, f.stats[t + XG] / 100.0, home)
            }

    fun averages(world: World, clubId: Long, compId: Int? = null): TeamAverages? {
        val fx = world.compSeasons.values.flatMap { it.fixtures }
            .filter { it.played && (it.homeId == clubId || it.awayId == clubId) && it.stats.size >= 26 && (compId == null || it.compId == compId) }
        if (fx.isEmpty()) return null
        var gf = 0.0; var ga = 0.0; var xf = 0.0; var xa = 0.0; var sf = 0.0; var sa = 0.0; var poss = 0.0; var pass = 0.0; var pc = 0.0; var cor = 0.0; var fou = 0.0
        for (f in fx) {
            val home = f.homeId == clubId
            val o = if (home) 0 else 13; val t = if (home) 13 else 0
            gf += if (home) f.homeGoals else f.awayGoals; ga += if (home) f.awayGoals else f.homeGoals
            xf += f.stats[o + XG] / 100.0; xa += f.stats[t + XG] / 100.0
            sf += f.stats[o + SHOTS]; sa += f.stats[t + SHOTS]
            val pt = (f.stats[o + POSS] + f.stats[t + POSS]).coerceAtLeast(1)
            poss += f.stats[o + POSS].toDouble() / pt
            pass += f.stats[o + PASSES]; pc += f.stats[o + PASS_C]
            cor += f.stats[o + CORNERS]; fou += f.stats[o + FOULS]
        }
        val n = fx.size.toDouble()
        return TeamAverages(fx.size, gf / n, ga / n, xf / n, xa / n, sf / n, sa / n, poss / n * 100, if (pass > 0) pc / pass * 100 else 0.0, cor / n, fou / n)
    }

    /** League average per club (for comparison). */
    fun leagueAverages(world: World, compId: Int): TeamAverages? {
        val clubs = world.compSeasons[compId]?.teams ?: return null
        val all = clubs.mapNotNull { averages(world, it, compId) }
        if (all.isEmpty()) return null
        fun avg(f: (TeamAverages) -> Double) = all.map(f).average()
        return TeamAverages(all.sumOf { it.matches } / all.size, avg { it.goalsFor }, avg { it.goalsAgainst }, avg { it.xgFor }, avg { it.xgAgainst },
            avg { it.shotsFor }, avg { it.shotsAgainst }, avg { it.possession }, avg { it.passPct }, avg { it.corners }, avg { it.fouls })
    }

    enum class Leader(val id: String, val en: String) {
        GOALS("Gol", "Goals"), ASSISTS("Assist", "Assists"), RATING("Rating rata-rata", "Average rating"), CLEAN_SHEETS("Clean sheet", "Clean sheets");
        fun label(lang: Lang) = if (lang == Lang.ID) id else en
    }

    fun leaders(world: World, compId: Int, kind: Leader, n: Int = 15): List<Pair<Player, Double>> {
        val clubs = world.compSeasons[compId]?.teams?.toSet() ?: return emptyList()
        val ps = world.players.values.filter { it.playingFor in clubs }
        return when (kind) {
            Leader.GOALS -> ps.map { it to it.season.goals.toDouble() }
            Leader.ASSISTS -> ps.map { it to it.season.assists.toDouble() }
            Leader.RATING -> ps.filter { it.season.apps >= 5 }.map { it to it.season.avgRating }
            Leader.CLEAN_SHEETS -> ps.filter { it.position == Position.GK }.map { it to it.season.cleanSheets.toDouble() }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(n)
    }

    fun per90(value: Int, minutes: Int): Double = if (minutes < 90) 0.0 else value * 90.0 / minutes
}
