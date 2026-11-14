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

/** How well a player suits a role: key attributes of the role plus position ability. */
object RoleFit {
    private val A = Attr.entries.associateBy { it.name }
    private fun k(vararg n: Attr) = n.toList()
    val keys: Map<Role, List<Attr>> = mapOf(
        Role.GOALKEEPER to k(Attr.HANDLING, Attr.REFLEXES, Attr.ONE_ON_ONES, Attr.POSITIONING, Attr.AERIAL_REACH),
        Role.SWEEPER_KEEPER to k(Attr.RUSHING_OUT, Attr.ONE_ON_ONES, Attr.KICKING, Attr.PASSING, Attr.ANTICIPATION, Attr.REFLEXES),
        Role.CENTRE_BACK to k(Attr.MARKING, Attr.TACKLING, Attr.HEADING, Attr.POSITIONING, Attr.STRENGTH),
        Role.BALL_PLAYING_DEFENDER to k(Attr.PASSING, Attr.COMPOSURE, Attr.VISION, Attr.MARKING, Attr.TACKLING),
        Role.NO_NONSENSE_CB to k(Attr.HEADING, Attr.MARKING, Attr.STRENGTH, Attr.BRAVERY, Attr.TACKLING),
        Role.LIBERO to k(Attr.PASSING, Attr.DRIBBLING, Attr.ANTICIPATION, Attr.COMPOSURE, Attr.TACKLING),
        Role.FULL_BACK to k(Attr.TACKLING, Attr.MARKING, Attr.POSITIONING, Attr.STAMINA, Attr.CROSSING),
        Role.WING_BACK to k(Attr.CROSSING, Attr.STAMINA, Attr.PACE, Attr.WORK_RATE, Attr.DRIBBLING),
        Role.INVERTED_FULL_BACK to k(Attr.PASSING, Attr.DECISIONS, Attr.POSITIONING, Attr.TACKLING, Attr.COMPOSURE),
        Role.COMPLETE_WING_BACK to k(Attr.CROSSING, Attr.DRIBBLING, Attr.STAMINA, Attr.PACE, Attr.FLAIR),
        Role.ANCHOR to k(Attr.POSITIONING, Attr.TACKLING, Attr.MARKING, Attr.CONCENTRATION, Attr.DECISIONS),
        Role.HALF_BACK to k(Attr.POSITIONING, Attr.PASSING, Attr.TACKLING, Attr.DECISIONS, Attr.COMPOSURE),
        Role.DEEP_PLAYMAKER to k(Attr.PASSING, Attr.VISION, Attr.COMPOSURE, Attr.DECISIONS, Attr.TECHNIQUE),
        Role.BALL_WINNER to k(Attr.TACKLING, Attr.AGGRESSION, Attr.WORK_RATE, Attr.STAMINA, Attr.ANTICIPATION),
        Role.BOX_TO_BOX to k(Attr.STAMINA, Attr.WORK_RATE, Attr.PASSING, Attr.TACKLING, Attr.OFF_THE_BALL),
        Role.CENTRAL_MIDFIELDER to k(Attr.PASSING, Attr.DECISIONS, Attr.TEAMWORK, Attr.FIRST_TOUCH, Attr.WORK_RATE),
        Role.MEZZALA to k(Attr.DRIBBLING, Attr.PASSING, Attr.OFF_THE_BALL, Attr.TECHNIQUE, Attr.VISION),
        Role.ROAMING_PLAYMAKER to k(Attr.PASSING, Attr.VISION, Attr.STAMINA, Attr.DRIBBLING, Attr.DECISIONS),
        Role.ADVANCED_PLAYMAKER to k(Attr.VISION, Attr.PASSING, Attr.TECHNIQUE, Attr.FIRST_TOUCH, Attr.FLAIR),
        Role.WIDE_MIDFIELDER to k(Attr.CROSSING, Attr.WORK_RATE, Attr.PASSING, Attr.STAMINA, Attr.TEAMWORK),
        Role.WINGER to k(Attr.PACE, Attr.ACCELERATION, Attr.DRIBBLING, Attr.CROSSING, Attr.AGILITY),
        Role.INVERTED_WINGER to k(Attr.DRIBBLING, Attr.PASSING, Attr.LONG_SHOTS, Attr.TECHNIQUE, Attr.FLAIR),
        Role.INSIDE_FORWARD to k(Attr.DRIBBLING, Attr.FINISHING, Attr.PACE, Attr.OFF_THE_BALL, Attr.FIRST_TOUCH),
        Role.WIDE_TARGET_MAN to k(Attr.HEADING, Attr.STRENGTH, Attr.JUMPING, Attr.BRAVERY, Attr.FIRST_TOUCH),
        Role.ATTACKING_MIDFIELDER to k(Attr.PASSING, Attr.LONG_SHOTS, Attr.OFF_THE_BALL, Attr.TECHNIQUE, Attr.FIRST_TOUCH),
        Role.SHADOW_STRIKER to k(Attr.OFF_THE_BALL, Attr.FINISHING, Attr.ANTICIPATION, Attr.COMPOSURE, Attr.DRIBBLING),
        Role.TARGET_FORWARD to k(Attr.HEADING, Attr.STRENGTH, Attr.JUMPING, Attr.BRAVERY, Attr.FIRST_TOUCH),
        Role.POACHER to k(Attr.FINISHING, Attr.OFF_THE_BALL, Attr.ANTICIPATION, Attr.COMPOSURE, Attr.ACCELERATION),
        Role.COMPLETE_FORWARD to k(Attr.FINISHING, Attr.PASSING, Attr.DRIBBLING, Attr.HEADING, Attr.TECHNIQUE),
        Role.PRESSING_FORWARD to k(Attr.WORK_RATE, Attr.STAMINA, Attr.AGGRESSION, Attr.ANTICIPATION, Attr.FINISHING),
        Role.FALSE_NINE to k(Attr.PASSING, Attr.VISION, Attr.DRIBBLING, Attr.TECHNIQUE, Attr.OFF_THE_BALL),
        Role.DEEP_LYING_FORWARD to k(Attr.PASSING, Attr.FIRST_TOUCH, Attr.VISION, Attr.TEAMWORK, Attr.FINISHING),
    )

    /** Suitability on the CA scale (1–200). */
    fun rating(p: Player, role: Role, pos: Position): Double {
        val key = keys[role] ?: return Ability.effective(p, pos)
        val keyAvg = key.map { p.attr(it) }.average()
        val fam = Ability.familiarity(p, pos)
        return (0.6 * Ability.caAt(p.attrs, pos) + 0.4 * (keyAvg - 1.0) / 19.0 * 200.0) * fam
    }

    /** 0.5–5 stars relative to the player's own best role in that position. */
    fun stars(p: Player, role: Role, pos: Position): Double {
        val best = Role.forPosition(pos).maxOf { rating(p, it, pos) }.coerceAtLeast(1.0)
        val r = rating(p, role, pos) / best
        return (5.0 * r - (1 - r) * 10).coerceIn(0.5, 5.0).let { Math.round(it * 2) / 2.0 }
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
