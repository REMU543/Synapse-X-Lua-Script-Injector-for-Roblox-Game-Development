package fm.engine.ability

import fm.core.*
import fm.core.Attr.*

/**
 * Current-ability model. CA (1–200) is a weighted average of the attributes that matter for a
 * position. Attributes are the source of truth; CA is derived and cached on the player.
 */
object Ability {
    private fun w(vararg pairs: Pair<Attr, Int>): DoubleArray {
        val arr = DoubleArray(Attr.COUNT)
        for ((a, v) in pairs) arr[a.ordinal] = v.toDouble()
        return arr
    }

    private val GK_W = w(HANDLING to 5, REFLEXES to 5, ONE_ON_ONES to 4, AERIAL_REACH to 4, COMMAND_OF_AREA to 3,
        KICKING to 2, THROWING to 2, RUSHING_OUT to 2, COMMUNICATION to 2, PUNCHING to 1, POSITIONING to 4,
        CONCENTRATION to 3, DECISIONS to 3, ANTICIPATION to 2, COMPOSURE to 2, AGILITY to 3, JUMPING to 1)
    private val CB_W = w(MARKING to 5, TACKLING to 5, HEADING to 5, POSITIONING to 5, JUMPING to 4, STRENGTH to 4,
        ANTICIPATION to 3, CONCENTRATION to 3, DECISIONS to 3, BRAVERY to 2, COMPOSURE to 2, PACE to 2,
        ACCELERATION to 2, PASSING to 2, FIRST_TOUCH to 1, AGGRESSION to 1, TEAMWORK to 1, STAMINA to 1)
    private val FB_W = w(TACKLING to 4, MARKING to 3, POSITIONING to 3, PACE to 4, ACCELERATION to 3, STAMINA to 4,
        CROSSING to 3, PASSING to 2, DRIBBLING to 2, WORK_RATE to 3, TEAMWORK to 2, ANTICIPATION to 2,
        CONCENTRATION to 2, DECISIONS to 2, AGILITY to 2, FIRST_TOUCH to 1, TECHNIQUE to 1, OFF_THE_BALL to 1)
    private val DM_W = w(TACKLING to 4, MARKING to 3, POSITIONING to 4, PASSING to 4, ANTICIPATION to 4, DECISIONS to 4,
        TEAMWORK to 3, WORK_RATE to 3, STAMINA to 3, CONCENTRATION to 3, STRENGTH to 2, FIRST_TOUCH to 2,
        COMPOSURE to 2, VISION to 2, TECHNIQUE to 2, AGGRESSION to 1)
    private val MC_W = w(PASSING to 5, VISION to 4, DECISIONS to 4, FIRST_TOUCH to 3, TECHNIQUE to 3, TEAMWORK to 3,
        STAMINA to 3, WORK_RATE to 3, ANTICIPATION to 3, COMPOSURE to 3, TACKLING to 2, OFF_THE_BALL to 2,
        DRIBBLING to 2, LONG_SHOTS to 1, POSITIONING to 2, ACCELERATION to 1, PACE to 1)
    private val WM_W = w(CROSSING to 4, DRIBBLING to 3, PASSING to 3, PACE to 4, ACCELERATION to 3, STAMINA to 4,
        WORK_RATE to 3, TEAMWORK to 2, OFF_THE_BALL to 3, TECHNIQUE to 2, FIRST_TOUCH to 2, DECISIONS to 2,
        VISION to 1, AGILITY to 2, TACKLING to 1)
    private val AMC_W = w(PASSING to 4, VISION to 5, TECHNIQUE to 4, FIRST_TOUCH to 4, DRIBBLING to 3, DECISIONS to 4,
        FLAIR to 3, OFF_THE_BALL to 3, COMPOSURE to 3, LONG_SHOTS to 2, FINISHING to 2, AGILITY to 2,
        ANTICIPATION to 2, ACCELERATION to 2, BALANCE to 1)
    private val W_W = w(DRIBBLING to 5, PACE to 5, ACCELERATION to 5, CROSSING to 3, TECHNIQUE to 3, FIRST_TOUCH to 3,
        FLAIR to 3, AGILITY to 3, OFF_THE_BALL to 3, FINISHING to 2, PASSING to 2, DECISIONS to 2, BALANCE to 2,
        ANTICIPATION to 1, COMPOSURE to 1, STAMINA to 2)
    private val ST_W = w(FINISHING to 5, OFF_THE_BALL to 4, COMPOSURE to 4, ANTICIPATION to 4, FIRST_TOUCH to 3,
        HEADING to 3, PACE to 3, ACCELERATION to 3, DRIBBLING to 2, TECHNIQUE to 2, STRENGTH to 2, JUMPING to 2,
        DECISIONS to 2, AGILITY to 2, BALANCE to 1, PASSING to 1, LONG_SHOTS to 1)

    fun weights(p: Position): DoubleArray = when (p) {
        Position.GK -> GK_W
        Position.DC -> CB_W
        Position.DR, Position.DL -> FB_W
        Position.DM -> DM_W
        Position.MC -> MC_W
        Position.MR, Position.ML -> WM_W
        Position.AMC -> AMC_W
        Position.AMR, Position.AML -> W_W
        Position.ST -> ST_W
    }

    private val weightSums = Position.entries.associateWith { weights(it).sum() }

    /** Weighted attribute average (1–20) for a position. */
    fun weightedAverage(attrs: ShortArray, p: Position): Double {
        val w = weights(p)
        var s = 0.0
        for (i in w.indices) if (w[i] > 0) s += w[i] * attrs[i] / 100.0
        return s / weightSums.getValue(p)
    }

    fun caFromAverage(avg: Double): Int = Math.round((avg - 1.0) / 19.0 * 200.0).toInt().coerceIn(1, 200)

    fun caAt(attrs: ShortArray, p: Position): Int = caFromAverage(weightedAverage(attrs, p))

    fun recompute(player: Player) {
        player.ca = caAt(player.attrs, player.position)
    }

    /** How comfortable a player is in a position: 1.0 natural … 0.55 completely unfamiliar. */
    fun familiarity(player: Player, p: Position): Double = when {
        p == player.position -> 1.0
        p in player.secondary -> 0.94
        player.position == Position.GK || p == Position.GK -> 0.3
        related(player.position, p) -> 0.82
        player.position.line == p.line -> 0.75
        else -> 0.58
    }

    private fun related(a: Position, b: Position): Boolean {
        val groups = listOf(
            setOf(Position.DR, Position.DL, Position.DC),
            setOf(Position.DM, Position.MC),
            setOf(Position.MC, Position.AMC),
            setOf(Position.MR, Position.AMR),
            setOf(Position.ML, Position.AML),
            setOf(Position.AMR, Position.AML, Position.ST),
            setOf(Position.AMC, Position.ST),
            setOf(Position.DR, Position.MR),
            setOf(Position.DL, Position.ML),
        )
        return groups.any { a in it && b in it }
    }

    /** Effective rating (CA scale) of a player in a position, including condition and morale. */
    fun effective(player: Player, p: Position): Double {
        val base = caAt(player.attrs, p) * familiarity(player, p)
        val cond = 0.75 + 0.25 * (player.condition / 100.0)
        val morale = 0.93 + 0.14 * (player.morale / 100.0)
        return base * cond * morale
    }

    /** 1–5 star rating relative to a reference CA (e.g. the league average). */
    fun stars(ca: Int, reference: Double): Double {
        val r = ca / reference
        return (2.5 + (r - 1.0) * 6.0).coerceIn(0.5, 5.0).let { Math.round(it * 2) / 2.0 }
    }
}

/** Aggregate team strength used by the reduced and light match models. */
data class TeamStrength(val attack: Double, val midfield: Double, val defense: Double, val gk: Double) {
    val overall get() = (attack + midfield + defense + gk) / 4.0
}

object Strength {
    fun of(lineup: List<Pair<Player, Position>>, instructions: TeamInstructions = TeamInstructions()): TeamStrength {
        if (lineup.isEmpty()) return TeamStrength(20.0, 20.0, 20.0, 20.0)
        var att = 0.0; var attN = 0.0
        var mid = 0.0; var midN = 0.0
        var def = 0.0; var defN = 0.0
        var gk = 30.0
        for ((p, pos) in lineup) {
            val e = Ability.effective(p, pos)
            when (pos.line) {
                Line.GK -> gk = e
                Line.DEF -> { def += e; defN += 1; mid += e * 0.15; midN += 0.15 }
                Line.MID -> { mid += e; midN += 1; att += e * 0.35; attN += 0.35; def += e * 0.3; defN += 0.3 }
                Line.ATT -> { att += e; attN += 1; mid += e * 0.3; midN += 0.3 }
            }
        }
        // Missing players (red cards) lower everything.
        val shortHanded = (11 - lineup.size).coerceAtLeast(0) * 0.06
        val m = instructions.mentality.value
        val a = (if (attN > 0) att / attN else 20.0) * (1 + 0.05 * m) * (1 - shortHanded)
        val d = (if (defN > 0) def / defN else 20.0) * (1 - 0.04 * m) * (1 - shortHanded)
        val mi = (if (midN > 0) mid / midN else 20.0) * (1 + 0.03 * (instructions.pressing - 0.5)) * (1 - shortHanded)
        return TeamStrength(a, mi, d, gk)
    }
}
