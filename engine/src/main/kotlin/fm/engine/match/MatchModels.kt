package fm.engine.match

import fm.core.*

/** A team as it enters a match: starting XI mapped to formation slots, plus the bench. */
class TeamSheet(
    val clubId: Long,
    val clubName: String,
    val color: Long,
    val altColor: Long,
    val starters: List<Pair<Player, Slot>>,
    val bench: List<Player>,
    var instructions: TeamInstructions,
    val formation: Formation,
    val maxSubs: Int,
    val familiarity: Double = 0.8,
    val penaltyTakerId: Long = -1,
    val freeKickTakerId: Long = -1,
    val cornerTakerId: Long = -1,
    val cornerRoutine: CornerRoutine = CornerRoutine.MIXED,
    val captainId: Long = -1,
)

class MatchSetup(
    val home: TeamSheet,
    val away: TeamSheet,
    val seed: Long,
    /** Knockout: a winner is required (extra time + penalties if level on aggregate). */
    val needsWinner: Boolean = false,
    /** Aggregate goals carried from a first leg (home-of-this-match, away-of-this-match). */
    val firstLeg: Pair<Int, Int> = 0 to 0,
    val neutral: Boolean = false,
    /** Index (0 home / 1 away) of the human-controlled team, or -1. */
    val userTeam: Int = -1,
    val importance: Double = 1.0,
)

enum class EvType {
    KICKOFF, HALFTIME, SECOND_HALF, FULLTIME, EXTRA_TIME, PENALTIES,
    GOAL, PEN_GOAL, PEN_MISS, SAVE, MISS, BLOCKED, CHANCE,
    YELLOW, RED, FOUL, OFFSIDE, CORNER, SUB, INJURY, TACTIC,
}

data class MatchEvent(
    val minute: Int,
    val type: EvType,
    /** 0 home, 1 away, -1 neutral. */
    val team: Int,
    val playerId: Long = -1,
    val otherId: Long = -1,
    val score: String = "",
)

class TeamStats {
    var shots = 0
    var onTarget = 0
    var xg = 0.0
    var passes = 0
    var passesCompleted = 0
    var fouls = 0
    var yellows = 0
    var reds = 0
    var corners = 0
    var offsides = 0
    var tackles = 0
    var saves = 0
    var possessionTicks = 0L
    fun toArray(): IntArray = intArrayOf(shots, onTarget, Math.round(xg * 100).toInt(), passes, passesCompleted,
        fouls, yellows, reds, corners, offsides, tackles, saves, possessionTicks.toInt())
}

class PlayerMatch(val playerId: Long, val team: Int) {
    var minutes = 0
    var goals = 0
    var assists = 0
    var rating = 6.5
    var yellow = 0
    var red = false
    var injuryDays = 0
    var injuryName = ""
    /** Remaining energy at the end (0–1). */
    var energy = 1.0
    var started = false
}

class MatchResult(
    val homeGoals: Int,
    val awayGoals: Int,
    val extraTime: Boolean,
    val penHome: Int,
    val penAway: Int,
    val events: List<MatchEvent>,
    val stats: Array<TeamStats>,
    val players: Map<Long, PlayerMatch>,
    val detail: Detail,
) {
    /** 0 home, 1 away, -1 draw (after aggregate, extra time and penalties where applicable). */
    var winner: Int = -1
}
