package fm.core

import kotlinx.serialization.Serializable

enum class Duty(val id: String, val en: String, val short: String) {
    DEFEND("Bertahan", "Defend", "B"),
    SUPPORT("Dukung", "Support", "D"),
    ATTACK("Serang", "Attack", "S");
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

/** Individual instructions for the player in a slot (each changes a term in the match engine). */
@Serializable
data class PlayerInstructions(
    val shootMore: Boolean = false,
    val dribbleMore: Boolean = false,
    val crossMore: Boolean = false,
    val closeDownMore: Boolean = false,
    val stayWider: Boolean = false,
    val holdPosition: Boolean = false,
    val getForward: Boolean = false,
    val tackleHarder: Boolean = false,
) {
    val count get() = listOf(shootMore, dribbleMore, crossMore, closeDownMore, stayWider, holdPosition, getForward, tackleHarder).count { it }
}

/** One of the 11 positions in a formation, in team-relative normalised coordinates. */
@Serializable
data class Slot(
    val position: Position,
    /** 0 = own goal line, 1 = opponent goal line. */
    val fx: Double,
    /** 0 = left touchline, 1 = right touchline (from the team's attacking view). */
    val fy: Double,
    val role: Role,
    val duty: Duty = Duty.SUPPORT,
    val instructions: PlayerInstructions = PlayerInstructions(),
)

enum class CornerRoutine(val id: String, val en: String) {
    MIXED("Campuran", "Mixed"),
    NEAR_POST("Tiang dekat", "Near post"),
    FAR_POST("Tiang jauh", "Far post"),
    PENALTY_SPOT("Titik penalti", "Penalty spot"),
    EDGE_OF_AREA("Tepi kotak (tembakan)", "Edge of area (shot)");
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

enum class Role(val id: String, val en: String) {
    GOALKEEPER("Penjaga Gawang", "Goalkeeper"),
    SWEEPER_KEEPER("Kiper Libero", "Sweeper Keeper"),
    CENTRE_BACK("Bek Tengah", "Centre-Back"),
    BALL_PLAYING_DEFENDER("Bek Pengumpan", "Ball-Playing Defender"),
    FULL_BACK("Bek Sayap", "Full-Back"),
    WING_BACK("Bek Sayap Menyerang", "Wing-Back"),
    INVERTED_FULL_BACK("Bek Sayap Terbalik", "Inverted Full-Back"),
    ANCHOR("Jangkar", "Anchor"),
    DEEP_PLAYMAKER("Pengatur Serangan Dalam", "Deep Playmaker"),
    BOX_TO_BOX("Gelandang Box-to-Box", "Box-to-Box Midfielder"),
    CENTRAL_MIDFIELDER("Gelandang Tengah", "Central Midfielder"),
    ADVANCED_PLAYMAKER("Playmaker Maju", "Advanced Playmaker"),
    WIDE_MIDFIELDER("Gelandang Sayap", "Wide Midfielder"),
    WINGER("Pemain Sayap", "Winger"),
    INSIDE_FORWARD("Penyerang Dalam", "Inside Forward"),
    ATTACKING_MIDFIELDER("Gelandang Serang", "Attacking Midfielder"),
    TARGET_FORWARD("Penyerang Target", "Target Forward"),
    POACHER("Pemburu Gol", "Poacher"),
    FALSE_NINE("False Nine", "False Nine"),
    COMPLETE_FORWARD("Penyerang Lengkap", "Complete Forward"),
    LIBERO("Libero", "Libero"),
    NO_NONSENSE_CB("Bek Tanpa Basa-basi", "No-Nonsense Centre-Back"),
    COMPLETE_WING_BACK("Bek Sayap Lengkap", "Complete Wing-Back"),
    HALF_BACK("Gelandang Bertahan Mundur", "Half-Back"),
    BALL_WINNER("Perebut Bola", "Ball-Winning Midfielder"),
    MEZZALA("Mezzala", "Mezzala"),
    ROAMING_PLAYMAKER("Playmaker Penjelajah", "Roaming Playmaker"),
    SHADOW_STRIKER("Penyerang Bayangan", "Shadow Striker"),
    INVERTED_WINGER("Pemain Sayap Terbalik", "Inverted Winger"),
    WIDE_TARGET_MAN("Target Man Sayap", "Wide Target Forward"),
    PRESSING_FORWARD("Penyerang Penekan", "Pressing Forward"),
    DEEP_LYING_FORWARD("Penyerang Turun", "Deep-Lying Forward");

    fun label(lang: Lang) = if (lang == Lang.ID) id else en

    companion object {
        /** Roles available for each position (the role menu of the tactics screen). */
        fun forPosition(p: Position): List<Role> = when (p) {
            Position.GK -> listOf(GOALKEEPER, SWEEPER_KEEPER)
            Position.DC -> listOf(CENTRE_BACK, BALL_PLAYING_DEFENDER, NO_NONSENSE_CB, LIBERO)
            Position.DR, Position.DL -> listOf(FULL_BACK, WING_BACK, INVERTED_FULL_BACK, COMPLETE_WING_BACK)
            Position.DM -> listOf(ANCHOR, HALF_BACK, DEEP_PLAYMAKER, BALL_WINNER)
            Position.MC -> listOf(CENTRAL_MIDFIELDER, BOX_TO_BOX, DEEP_PLAYMAKER, BALL_WINNER, MEZZALA, ROAMING_PLAYMAKER, ADVANCED_PLAYMAKER)
            Position.MR, Position.ML -> listOf(WIDE_MIDFIELDER, WINGER, INVERTED_WINGER, WING_BACK)
            Position.AMC -> listOf(ATTACKING_MIDFIELDER, ADVANCED_PLAYMAKER, SHADOW_STRIKER)
            Position.AMR, Position.AML -> listOf(WINGER, INVERTED_WINGER, INSIDE_FORWARD, WIDE_TARGET_MAN)
            Position.ST -> listOf(COMPLETE_FORWARD, POACHER, TARGET_FORWARD, PRESSING_FORWARD, FALSE_NINE, DEEP_LYING_FORWARD)
        }

        /** Duties a role allows (e.g. a poacher always attacks, an anchor always defends). */
        fun duties(r: Role): List<Duty> = when (r) {
            GOALKEEPER, ANCHOR, HALF_BACK, NO_NONSENSE_CB, CENTRE_BACK -> listOf(Duty.DEFEND)
            BALL_PLAYING_DEFENDER, LIBERO, SWEEPER_KEEPER -> listOf(Duty.DEFEND, Duty.SUPPORT)
            POACHER, SHADOW_STRIKER -> listOf(Duty.ATTACK)
            INSIDE_FORWARD -> listOf(Duty.ATTACK, Duty.SUPPORT)
            BOX_TO_BOX, ROAMING_PLAYMAKER -> listOf(Duty.SUPPORT)
            TARGET_FORWARD, PRESSING_FORWARD, COMPLETE_FORWARD, WIDE_TARGET_MAN -> listOf(Duty.SUPPORT, Duty.ATTACK)
            else -> listOf(Duty.DEFEND, Duty.SUPPORT, Duty.ATTACK)
        }
    }
}

@Serializable
data class Formation(val name: String, val slots: List<Slot>) {
    init { require(slots.size == 11) { "A formation needs 11 slots" } }

    companion object {
        private fun s(p: Position, fx: Double, fy: Double, r: Role) = Slot(p, fx, fy, r)
        private val gk = s(Position.GK, 0.02, 0.5, Role.GOALKEEPER)

        val F433 = Formation("4-3-3", listOf(gk,
            s(Position.DL, 0.22, 0.12, Role.FULL_BACK), s(Position.DC, 0.18, 0.37, Role.CENTRE_BACK),
            s(Position.DC, 0.18, 0.63, Role.CENTRE_BACK), s(Position.DR, 0.22, 0.88, Role.FULL_BACK),
            s(Position.DM, 0.36, 0.5, Role.ANCHOR), s(Position.MC, 0.47, 0.3, Role.BOX_TO_BOX),
            s(Position.MC, 0.47, 0.7, Role.CENTRAL_MIDFIELDER),
            s(Position.AML, 0.68, 0.14, Role.INSIDE_FORWARD), s(Position.ST, 0.76, 0.5, Role.COMPLETE_FORWARD),
            s(Position.AMR, 0.68, 0.86, Role.WINGER)))

        val F4231 = Formation("4-2-3-1", listOf(gk,
            s(Position.DL, 0.22, 0.12, Role.FULL_BACK), s(Position.DC, 0.18, 0.37, Role.CENTRE_BACK),
            s(Position.DC, 0.18, 0.63, Role.CENTRE_BACK), s(Position.DR, 0.22, 0.88, Role.FULL_BACK),
            s(Position.DM, 0.37, 0.38, Role.DEEP_PLAYMAKER), s(Position.DM, 0.37, 0.62, Role.BOX_TO_BOX),
            s(Position.AML, 0.62, 0.15, Role.WINGER), s(Position.AMC, 0.6, 0.5, Role.ADVANCED_PLAYMAKER),
            s(Position.AMR, 0.62, 0.85, Role.INSIDE_FORWARD), s(Position.ST, 0.78, 0.5, Role.POACHER)))

        val F442 = Formation("4-4-2", listOf(gk,
            s(Position.DL, 0.22, 0.12, Role.FULL_BACK), s(Position.DC, 0.18, 0.37, Role.CENTRE_BACK),
            s(Position.DC, 0.18, 0.63, Role.CENTRE_BACK), s(Position.DR, 0.22, 0.88, Role.FULL_BACK),
            s(Position.ML, 0.45, 0.13, Role.WIDE_MIDFIELDER), s(Position.MC, 0.42, 0.38, Role.BOX_TO_BOX),
            s(Position.MC, 0.42, 0.62, Role.CENTRAL_MIDFIELDER), s(Position.MR, 0.45, 0.87, Role.WIDE_MIDFIELDER),
            s(Position.ST, 0.74, 0.38, Role.TARGET_FORWARD), s(Position.ST, 0.74, 0.62, Role.POACHER)))

        val F352 = Formation("3-5-2", listOf(gk,
            s(Position.DC, 0.18, 0.25, Role.CENTRE_BACK), s(Position.DC, 0.16, 0.5, Role.BALL_PLAYING_DEFENDER),
            s(Position.DC, 0.18, 0.75, Role.CENTRE_BACK),
            s(Position.ML, 0.42, 0.08, Role.WING_BACK), s(Position.DM, 0.36, 0.5, Role.ANCHOR),
            s(Position.MC, 0.48, 0.32, Role.BOX_TO_BOX), s(Position.MC, 0.48, 0.68, Role.ADVANCED_PLAYMAKER),
            s(Position.MR, 0.42, 0.92, Role.WING_BACK),
            s(Position.ST, 0.75, 0.38, Role.COMPLETE_FORWARD), s(Position.ST, 0.75, 0.62, Role.POACHER)))

        val F343 = Formation("3-4-3", listOf(gk,
            s(Position.DC, 0.18, 0.25, Role.CENTRE_BACK), s(Position.DC, 0.16, 0.5, Role.CENTRE_BACK),
            s(Position.DC, 0.18, 0.75, Role.BALL_PLAYING_DEFENDER),
            s(Position.ML, 0.42, 0.08, Role.WING_BACK), s(Position.MC, 0.42, 0.37, Role.DEEP_PLAYMAKER),
            s(Position.MC, 0.42, 0.63, Role.BOX_TO_BOX), s(Position.MR, 0.42, 0.92, Role.WING_BACK),
            s(Position.AML, 0.68, 0.18, Role.INSIDE_FORWARD), s(Position.ST, 0.77, 0.5, Role.COMPLETE_FORWARD),
            s(Position.AMR, 0.68, 0.82, Role.INSIDE_FORWARD)))

        val F4141 = Formation("4-1-4-1", listOf(gk,
            s(Position.DL, 0.22, 0.12, Role.FULL_BACK), s(Position.DC, 0.18, 0.37, Role.CENTRE_BACK),
            s(Position.DC, 0.18, 0.63, Role.CENTRE_BACK), s(Position.DR, 0.22, 0.88, Role.FULL_BACK),
            s(Position.DM, 0.34, 0.5, Role.ANCHOR),
            s(Position.ML, 0.5, 0.13, Role.WINGER), s(Position.MC, 0.5, 0.38, Role.BOX_TO_BOX),
            s(Position.MC, 0.5, 0.62, Role.CENTRAL_MIDFIELDER), s(Position.MR, 0.5, 0.87, Role.WINGER),
            s(Position.ST, 0.77, 0.5, Role.COMPLETE_FORWARD)))

        val ALL = listOf(F433, F4231, F442, F352, F343, F4141)
        fun byName(name: String) = ALL.firstOrNull { it.name == name } ?: F442
    }
}

enum class Mentality(val id: String, val en: String, val value: Double) {
    VERY_DEFENSIVE("Sangat Bertahan", "Very Defensive", -1.0),
    DEFENSIVE("Bertahan", "Defensive", -0.5),
    BALANCED("Seimbang", "Balanced", 0.0),
    POSITIVE("Positif", "Positive", 0.4),
    ATTACKING("Menyerang", "Attacking", 0.8),
    VERY_ATTACKING("Sangat Menyerang", "Very Attacking", 1.0);
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

/**
 * Team instructions. Every value is in [0, 1] (0.5 = default) and is consumed numerically
 * by the match engine (see MatchEngine2D), so each one has a concrete effect on play.
 */
@Serializable
data class TeamInstructions(
    val mentality: Mentality = Mentality.BALANCED,
    /** Short (0) to very direct (1) passing. */
    val directness: Double = 0.5,
    /** Slow, patient (0) to very high tempo (1). */
    val tempo: Double = 0.5,
    /** Narrow (0) to wide (1) attacking width. */
    val width: Double = 0.5,
    /** Cross from deep / wide frequency. */
    val crossing: Double = 0.5,
    /** Deep (0) to high (1) defensive line. */
    val defensiveLine: Double = 0.5,
    /** Low (0) to high (1) line of engagement. */
    val engagement: Double = 0.5,
    /** Pressing intensity. */
    val pressing: Double = 0.5,
    /** Narrow (0) to wide (1) defensive width. */
    val defensiveWidth: Double = 0.5,
    /** Zonal (0) to tight man marking (1). */
    val tightMarking: Double = 0.5,
    val offsideTrap: Boolean = false,
    /** After losing the ball: regroup (0) or counter-press (1). */
    val counterPress: Double = 0.5,
    /** After winning the ball: hold shape (0) or counter-attack (1). */
    val counterAttack: Double = 0.5,
    /** Goalkeeper distribution: short (0) or long (1). */
    val gkDistribution: Double = 0.5,
    /** Shoot on sight (1) vs work the ball into the box (0). */
    val shootingRange: Double = 0.5,
)

@Serializable
data class Tactic(
    val formation: Formation = Formation.F442,
    val instructions: TeamInstructions = TeamInstructions(),
    /** Optional user-picked starting XI (player ids, by slot). Empty = auto-pick. */
    val lineup: List<Long> = emptyList(),
    val bench: List<Long> = emptyList(),
    val captainId: Long = -1,
    val penaltyTakerId: Long = -1,
    val freeKickTakerId: Long = -1,
    val cornerTakerId: Long = -1,
    /** Per-slot overrides (same order as formation slots); empty = formation defaults. */
    val slotRoles: List<Role> = emptyList(),
    val slotDuties: List<Duty> = emptyList(),
    val slotInstructions: List<PlayerInstructions> = emptyList(),
    val cornerRoutine: CornerRoutine = CornerRoutine.MIXED,
) {
    /** The formation's slots with this tactic's roles, duties and player instructions applied. */
    fun effectiveSlots(): List<Slot> = formation.slots.mapIndexed { i, s ->
        val role = slotRoles.getOrNull(i)?.takeIf { it in Role.forPosition(s.position) } ?: s.role
        val duty = slotDuties.getOrNull(i)?.takeIf { it in Role.duties(role) } ?: Role.duties(role).let { if (s.duty in it) s.duty else it.first() }
        s.copy(role = role, duty = duty, instructions = slotInstructions.getOrNull(i) ?: s.instructions)
    }

    fun withSlot(i: Int, role: Role? = null, duty: Duty? = null, instr: PlayerInstructions? = null): Tactic {
        val eff = effectiveSlots()
        val roles = eff.map { it.role }.toMutableList()
        val duties = eff.map { it.duty }.toMutableList()
        val ins = eff.map { it.instructions }.toMutableList()
        if (role != null && role != roles[i]) { roles[i] = role; duties[i] = Role.duties(role).first() }
        if (duty != null && duty in Role.duties(roles[i])) duties[i] = duty
        if (instr != null) ins[i] = instr
        return copy(slotRoles = roles, slotDuties = duties, slotInstructions = ins)
    }
}
