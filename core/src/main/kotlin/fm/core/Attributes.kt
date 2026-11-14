package fm.core

import kotlinx.serialization.Serializable

enum class AttrGroup { TECHNICAL, MENTAL, PHYSICAL, GOALKEEPING }

/** Player attributes on a 1–20 scale (stored ×100 for smooth development). */
enum class Attr(val group: AttrGroup, val id: String, val en: String) {
    // Technical
    CORNERS(AttrGroup.TECHNICAL, "Tendangan Sudut", "Corners"),
    CROSSING(AttrGroup.TECHNICAL, "Umpan Silang", "Crossing"),
    DRIBBLING(AttrGroup.TECHNICAL, "Menggiring", "Dribbling"),
    FINISHING(AttrGroup.TECHNICAL, "Penyelesaian", "Finishing"),
    FIRST_TOUCH(AttrGroup.TECHNICAL, "Kontrol Pertama", "First Touch"),
    FREE_KICK(AttrGroup.TECHNICAL, "Tendangan Bebas", "Free Kicks"),
    HEADING(AttrGroup.TECHNICAL, "Sundulan", "Heading"),
    LONG_SHOTS(AttrGroup.TECHNICAL, "Tembakan Jauh", "Long Shots"),
    LONG_THROWS(AttrGroup.TECHNICAL, "Lemparan Jauh", "Long Throws"),
    MARKING(AttrGroup.TECHNICAL, "Penjagaan", "Marking"),
    PASSING(AttrGroup.TECHNICAL, "Umpan", "Passing"),
    PENALTY(AttrGroup.TECHNICAL, "Penalti", "Penalty Taking"),
    TACKLING(AttrGroup.TECHNICAL, "Tekel", "Tackling"),
    TECHNIQUE(AttrGroup.TECHNICAL, "Teknik", "Technique"),
    // Mental
    AGGRESSION(AttrGroup.MENTAL, "Agresivitas", "Aggression"),
    ANTICIPATION(AttrGroup.MENTAL, "Antisipasi", "Anticipation"),
    BRAVERY(AttrGroup.MENTAL, "Keberanian", "Bravery"),
    COMPOSURE(AttrGroup.MENTAL, "Ketenangan", "Composure"),
    CONCENTRATION(AttrGroup.MENTAL, "Konsentrasi", "Concentration"),
    DECISIONS(AttrGroup.MENTAL, "Keputusan", "Decisions"),
    DETERMINATION(AttrGroup.MENTAL, "Determinasi", "Determination"),
    FLAIR(AttrGroup.MENTAL, "Kreativitas", "Flair"),
    LEADERSHIP(AttrGroup.MENTAL, "Kepemimpinan", "Leadership"),
    OFF_THE_BALL(AttrGroup.MENTAL, "Pergerakan", "Off the Ball"),
    POSITIONING(AttrGroup.MENTAL, "Penempatan Posisi", "Positioning"),
    TEAMWORK(AttrGroup.MENTAL, "Kerja Sama", "Teamwork"),
    VISION(AttrGroup.MENTAL, "Visi", "Vision"),
    WORK_RATE(AttrGroup.MENTAL, "Etos Kerja", "Work Rate"),
    // Physical
    ACCELERATION(AttrGroup.PHYSICAL, "Akselerasi", "Acceleration"),
    AGILITY(AttrGroup.PHYSICAL, "Kelincahan", "Agility"),
    BALANCE(AttrGroup.PHYSICAL, "Keseimbangan", "Balance"),
    JUMPING(AttrGroup.PHYSICAL, "Lompatan", "Jumping Reach"),
    NATURAL_FITNESS(AttrGroup.PHYSICAL, "Kebugaran Alami", "Natural Fitness"),
    PACE(AttrGroup.PHYSICAL, "Kecepatan", "Pace"),
    STAMINA(AttrGroup.PHYSICAL, "Stamina", "Stamina"),
    STRENGTH(AttrGroup.PHYSICAL, "Kekuatan", "Strength"),
    // Goalkeeping
    AERIAL_REACH(AttrGroup.GOALKEEPING, "Jangkauan Udara", "Aerial Reach"),
    COMMAND_OF_AREA(AttrGroup.GOALKEEPING, "Penguasaan Kotak", "Command of Area"),
    COMMUNICATION(AttrGroup.GOALKEEPING, "Komunikasi", "Communication"),
    HANDLING(AttrGroup.GOALKEEPING, "Tangkapan", "Handling"),
    KICKING(AttrGroup.GOALKEEPING, "Tendangan", "Kicking"),
    ONE_ON_ONES(AttrGroup.GOALKEEPING, "Satu Lawan Satu", "One on Ones"),
    REFLEXES(AttrGroup.GOALKEEPING, "Refleks", "Reflexes"),
    RUSHING_OUT(AttrGroup.GOALKEEPING, "Keluar Sarang", "Rushing Out"),
    THROWING(AttrGroup.GOALKEEPING, "Lemparan", "Throwing"),
    PUNCHING(AttrGroup.GOALKEEPING, "Meninju Bola", "Punching");

    fun label(lang: Lang) = if (lang == Lang.ID) id else en

    companion object {
        val COUNT = entries.size
        val outfield = entries.filter { it.group != AttrGroup.GOALKEEPING }
    }
}

enum class Position(val code: String, val codeId: String, val line: Line) {
    GK("GK", "PG", Line.GK),
    DR("DR", "BKa", Line.DEF), DC("DC", "BT", Line.DEF), DL("DL", "BKi", Line.DEF),
    DM("DM", "GBT", Line.MID),
    MR("MR", "GKa", Line.MID), MC("MC", "GT", Line.MID), ML("ML", "GKi", Line.MID),
    AMR("AMR", "SKa", Line.ATT), AMC("AMC", "GSB", Line.ATT), AML("AML", "SKi", Line.ATT),
    ST("ST", "PD", Line.ATT);

    fun label(lang: Lang) = if (lang == Lang.ID) codeId else code
}

enum class Line { GK, DEF, MID, ATT }

enum class Foot { RIGHT, LEFT, BOTH }

/** Hidden personality traits, 1–20. */
@Serializable
data class Personality(
    val professionalism: Int,
    val ambition: Int,
    val pressure: Int,
    val temperament: Int,
    val loyalty: Int,
    val adaptability: Int,
    val consistency: Int,
    val bigMatches: Int,
) {
    fun describe(lang: Lang): String {
        val id = lang == Lang.ID
        return when {
            professionalism >= 17 && ambition >= 15 -> if (id) "Sangat Profesional" else "Model Professional"
            professionalism >= 15 -> if (id) "Profesional" else "Professional"
            ambition >= 17 -> if (id) "Ambisius" else "Driven"
            loyalty >= 17 -> if (id) "Setia" else "Loyal"
            temperament <= 5 -> if (id) "Temperamental" else "Volatile"
            professionalism <= 6 -> if (id) "Santai" else "Casual"
            else -> if (id) "Seimbang" else "Balanced"
        }
    }
}
