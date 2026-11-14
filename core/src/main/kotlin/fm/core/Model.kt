package fm.core

import kotlinx.serialization.Serializable

enum class Continent { ASIA, EUROPE, AFRICA, SOUTH_AMERICA, NORTH_AMERICA, OCEANIA }

@Serializable
data class Nation(
    val id: Int,
    val code: String,
    val nameId: String,
    val nameEn: String,
    val continent: Continent,
    /** 1–100 football reputation. */
    val reputation: Int,
    /** 1–20 quality of youth produced. */
    val youthRating: Int,
    /** Name-pool key for procedural names. */
    val namePool: String,
    /** Monthly wage of a CA-60 player at a mid club, in Rupiah. */
    val wageIndex: Long,
    /** Average ticket price in Rupiah. */
    val ticketPrice: Long,
    /** Whether leagues of this nation are simulated in detail (playable) or lightweight. */
    val playable: Boolean,
) {
    fun name(lang: Lang) = if (lang == Lang.ID) nameId else nameEn
}

@Serializable
data class Contract(
    val clubId: Long,
    val startDay: Int,
    /** Contract ends on 30 June of this year. */
    val endYear: Int,
    /** Monthly wage, Rupiah. */
    val wage: Long,
    val releaseClause: Long = 0,
    val sellOnPercent: Int = 0,
    val squadStatus: SquadStatus = SquadStatus.ROTATION,
    val youth: Boolean = false,
)

enum class SquadStatus(val id: String, val en: String) {
    KEY("Pemain Kunci", "Key Player"),
    FIRST_TEAM("Tim Utama", "First Team"),
    ROTATION("Rotasi", "Rotation"),
    BACKUP("Cadangan", "Backup"),
    PROSPECT("Prospek Muda", "Hot Prospect");
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

@Serializable
class SeasonStats(
    var apps: Int = 0,
    var minutes: Int = 0,
    var goals: Int = 0,
    var assists: Int = 0,
    var yellow: Int = 0,
    var red: Int = 0,
    var ratingSum: Int = 0, // rating ×100 summed over appearances
    var cleanSheets: Int = 0,
) {
    val avgRating: Double get() = if (apps == 0) 0.0 else ratingSum / 100.0 / apps
}

@Serializable
data class SeasonRecord(
    val season: Int,
    val clubId: Long,
    val clubName: String,
    val apps: Int,
    val goals: Int,
    val assists: Int,
    val avgRating: Double,
    val ca: Int,
)

@Serializable
class Player(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val nationId: Int,
    val birthDay: Int,
    var position: Position,
    var secondary: List<Position>,
    val foot: Foot,
    val heightCm: Int,
    val weightKg: Int,
    /** Attribute values ×100 (100…2000), indexed by [Attr.ordinal]. */
    val attrs: ShortArray,
    /** Potential ability ceiling, 1–200. */
    var pa: Int,
    val personality: Personality,
    /** 1–20, higher = more injury prone. */
    val injuryProneness: Int,
    var clubId: Long = -1,
    var contract: Contract? = null,
    var loanClubId: Long = -1,
    var loanUntilDay: Int = 0,
    /** Current ability (derived from attributes; cached). */
    var ca: Int = 0,
    var morale: Int = 70,
    /** Match condition 0–100. */
    var condition: Int = 100,
    var injuryDays: Int = 0,
    var injuryName: String = "",
    var suspended: Int = 0,
    var value: Long = 0,
    /** 1–100. */
    var reputation: Int = 10,
    var transferListed: Boolean = false,
    var season: SeasonStats = SeasonStats(),
    val history: MutableList<SeasonRecord> = mutableListOf(),
    var internationalCaps: Int = 0,
    var joinedDay: Int = 0,
    /** Weeks without first-team football this season (affects morale & development). */
    var benchWeeks: Int = 0,
    /** Relationship with the (user) manager, 0–100. */
    var relationship: Int = 50,
    /** Cached happiness 0–100 (see Dynamics). */
    var happiness: Int = 65,
    /** Weeks the player has been seriously unhappy (drives requests). */
    var unhappyWeeks: Int = 0,
    /** True once the player has asked to leave. */
    var wantsOut: Boolean = false,
    /** Last day the manager talked privately with the player (limits spam). */
    var lastChatDay: Int = -999,
    /** Individual training: attribute to emphasise (null = none). */
    var individualAttr: Attr? = null,
    /** Individual training: position being learnt (null = none). */
    var retrainPosition: Position? = null,
    var retrainWeeks: Int = 0,
) {
    val name: String get() = "$firstName $lastName".trim()
    val shortName: String get() = if (firstName.isEmpty()) lastName else "${firstName.first()}. $lastName"
    /** Club the player currently plays for (loan club if on loan). */
    val playingFor: Long get() = if (loanClubId >= 0) loanClubId else clubId
    fun attr(a: Attr): Double = attrs[a.ordinal] / 100.0
    fun attrInt(a: Attr): Int = Math.round(attrs[a.ordinal] / 100.0).toInt().coerceIn(1, 20)
    fun age(today: Int) = Cal.age(birthDay, today)
    val available: Boolean get() = injuryDays <= 0 && suspended <= 0
}

enum class StaffRole(val id: String, val en: String) {
    MANAGER("Manajer", "Manager"),
    ASSISTANT("Asisten Manajer", "Assistant Manager"),
    COACH("Pelatih", "Coach"),
    GK_COACH("Pelatih Kiper", "Goalkeeping Coach"),
    FITNESS_COACH("Pelatih Fisik", "Fitness Coach"),
    SCOUT("Pemandu Bakat", "Scout"),
    PHYSIO("Fisioterapis", "Physio"),
    YOUTH_COACH("Pelatih Akademi", "Youth Coach"),
    ANALYST("Analis", "Analyst"),
    DIRECTOR_OF_FOOTBALL("Direktur Olahraga", "Director of Football");
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

@Serializable
data class ManagerStyle(
    val preferredFormation: String,
    /** -1 defensive … +1 attacking. */
    val attacking: Double,
    /** 0 possession … 1 direct. */
    val directness: Double,
    /** 0 low block … 1 high press. */
    val pressing: Double,
    /** 0 veterans … 1 trusts youth. */
    val youthTrust: Double,
    /** 0 cautious … 1 risk taker in the market. */
    val riskTolerance: Double,
)

@Serializable
class Staff(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val nationId: Int,
    val birthDay: Int,
    var role: StaffRole,
    /** Coaching attributes 1–20. */
    var coaching: Int,
    var tactical: Int,
    var judgingAbility: Int,
    var judgingPotential: Int,
    var motivation: Int,
    var discipline: Int,
    var medical: Int,
    var youthWork: Int,
    var reputation: Int,
    var style: ManagerStyle,
    var clubId: Long = -1,
    var contract: Contract? = null,
    val formerPlayer: Boolean = false,
    var retired: Boolean = false,
    val careerLog: MutableList<String> = mutableListOf(),
    var trophies: Int = 0,
) {
    val name: String get() = "$firstName $lastName".trim()
    fun age(today: Int) = Cal.age(birthDay, today)
    /** Overall quality for the given role, 1–20. */
    fun quality(): Double = when (role) {
        StaffRole.MANAGER, StaffRole.ASSISTANT -> (coaching + tactical * 2 + motivation + discipline) / 5.0
        StaffRole.COACH, StaffRole.FITNESS_COACH, StaffRole.GK_COACH -> (coaching * 2 + discipline + motivation) / 4.0
        StaffRole.SCOUT, StaffRole.DIRECTOR_OF_FOOTBALL -> (judgingAbility + judgingPotential) / 2.0
        StaffRole.PHYSIO -> medical.toDouble()
        StaffRole.YOUTH_COACH -> (youthWork * 2 + coaching) / 3.0
        StaffRole.ANALYST -> (tactical + judgingAbility) / 2.0
    }
}

@Serializable
class ClubFinance(
    /** Balance at the start of the current season (for the ledger invariant). */
    var openingBalance: Long = 0,
    /** Current season income and expense totals by category. */
    val income: MutableMap<String, Long> = linkedMapOf(),
    val expense: MutableMap<String, Long> = linkedMapOf(),
    /** Last 24 monthly snapshots: [epochDay, balance]. */
    val monthly: MutableList<LongArray> = mutableListOf(),
    /** Seasonal summaries: [season, income, expense, endBalance]. */
    val seasons: MutableList<LongArray> = mutableListOf(),
)

@Serializable
class Club(
    val id: Long,
    val nationId: Int,
    var name: String,
    var shortName: String,
    val city: String,
    var reputation: Int,
    val stadiumName: String,
    var stadiumCapacity: Int,
    var trainingFacilities: Int,
    var youthFacilities: Int,
    var youthRecruitment: Int,
    val primaryColor: Long,
    val secondaryColor: Long,
    val founded: Int,
    var balance: Long,
    var transferBudget: Long,
    /** Monthly wage budget, Rupiah. */
    var wageBudget: Long,
    var leagueId: Int,
    var managerId: Long = -1,
    val playerIds: MutableList<Long> = mutableListOf(),
    val staffIds: MutableList<Long> = mutableListOf(),
    var tactic: Tactic = Tactic(),
    /** 0–100. */
    var boardConfidence: Int = 60,
    /** Expected league finish (1 = champions). */
    var expectedPosition: Int = 10,
    val finance: ClubFinance = ClubFinance(),
    var fanBase: Int = 10_000,
    var trainingFocus: TrainingFocus = TrainingFocus.BALANCED,
    var trainingIntensity: Int = 2,
    val honours: MutableList<String> = mutableListOf(),
    val form: MutableList<Char> = mutableListOf(),
    var debtRescues: Int = 0,
    /** Annual sponsorship + commercial income at the club's reference reputation. */
    var commercialBase: Long = 0,
    var commercialRepRef: Int = 50,
    /** Weekly schedule Monday..Sunday (empty = derived from trainingFocus). */
    var weeklyPlan: MutableList<Session> = mutableListOf(),
)

/** One training session in the weekly schedule. */
enum class Session(val id: String, val en: String, val load: Int) {
    REST("Istirahat", "Rest", 0),
    RECOVERY("Pemulihan", "Recovery", 0),
    FITNESS("Fisik", "Fitness", 3),
    TECHNICAL("Teknik", "Technical", 2),
    TACTICAL("Taktik", "Tactical", 1),
    ATTACKING("Menyerang", "Attacking", 2),
    DEFENDING("Bertahan", "Defending", 2),
    SET_PIECES("Bola Mati", "Set Pieces", 1),
    MATCH_PREP("Persiapan Laga", "Match Preparation", 1);
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

enum class TrainingFocus(val id: String, val en: String) {
    BALANCED("Seimbang", "Balanced"),
    ATTACKING("Menyerang", "Attacking"),
    DEFENDING("Bertahan", "Defending"),
    PHYSICAL("Fisik", "Physical"),
    TECHNICAL("Teknik", "Technical"),
    TACTICAL("Taktik", "Tactical"),
    MATCH_PREP("Persiapan Laga", "Match Preparation");
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

enum class CompType { LEAGUE, CUP, CONTINENTAL }

enum class Tiebreaker { POINTS, GOAL_DIFFERENCE, GOALS_FOR, HEAD_TO_HEAD, WINS }

@Serializable
data class LeagueRules(
    val teams: Int,
    val rounds: Int = 2,
    val promote: Int = 0,
    val relegate: Int = 0,
    val upperLeagueId: Int = -1,
    val lowerLeagueId: Int = -1,
    val pointsWin: Int = 3,
    val pointsDraw: Int = 1,
    val tiebreakers: List<Tiebreaker> = listOf(Tiebreaker.POINTS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR, Tiebreaker.HEAD_TO_HEAD),
    /** Annual TV money per club, Rupiah. */
    val tvMoneyPerClub: Long = 0,
    /** Prize money for the champion; decreases linearly down the table. */
    val championPrize: Long = 0,
    /** Weekday of matches (6 = Saturday). */
    val matchDow: Int = 6,
    val maxSubs: Int = 5,
    val maxForeignInSquad: Int = 99,
)

@Serializable
data class CupRules(
    /** Leagues whose clubs enter. */
    val entrantLeagues: List<Int> = emptyList(),
    /** For continental cups: (nationId, places) qualification from top-tier leagues. */
    val qualification: List<IntArray> = emptyList(),
    val twoLegged: Boolean = false,
    val singleLegFinal: Boolean = true,
    val extraTime: Boolean = true,
    val awayGoals: Boolean = false,
    /** Month/day of each round. */
    val roundDates: List<IntArray> = emptyList(),
    val matchDow: Int = 3,
    val winnerPrize: Long = 0,
    val participationPrize: Long = 0,
)

/** Competition definition: data-driven, so new competitions need no new code. */
@Serializable
data class Competition(
    val id: Int,
    val nameId: String,
    val nameEn: String,
    val shortName: String,
    val type: CompType,
    val nationId: Int,
    val tier: Int,
    val reputation: Int,
    val league: LeagueRules? = null,
    val cup: CupRules? = null,
) {
    fun name(lang: Lang) = if (lang == Lang.ID) nameId else nameEn
}

enum class Detail { FULL, REDUCED, LIGHT }

@Serializable
class Fixture(
    val id: Long,
    val compId: Int,
    val round: Int,
    var day: Int,
    val homeId: Long,
    val awayId: Long,
    val leg: Int = 0,
    /** For second legs: id of the first-leg fixture. */
    val firstLegId: Long = -1,
    var played: Boolean = false,
    var homeGoals: Int = 0,
    var awayGoals: Int = 0,
    var extraTime: Boolean = false,
    var penHome: Int = -1,
    var penAway: Int = -1,
    var winnerId: Long = -1,
    var detail: Detail = Detail.LIGHT,
    var attendance: Int = 0,
    /** Compact event log (user matches & recent): "minute|type|team|playerId". */
    var events: List<String> = emptyList(),
    var stats: IntArray = IntArray(0),
)

@Serializable
class TableRow(
    val clubId: Long,
    var played: Int = 0,
    var won: Int = 0,
    var drawn: Int = 0,
    var lost: Int = 0,
    var gf: Int = 0,
    var ga: Int = 0,
    var points: Int = 0,
) {
    val gd get() = gf - ga
}

@Serializable
class CompSeason(
    val compId: Int,
    val season: Int,
    val teams: MutableList<Long>,
    val fixtures: MutableList<Fixture> = mutableListOf(),
    val table: MutableList<TableRow> = mutableListOf(),
    var currentRound: Int = 0,
    var finished: Boolean = false,
    var winnerId: Long = -1,
    var runnerUpId: Long = -1,
    /** Clubs still alive in a cup. */
    val alive: MutableList<Long> = mutableListOf(),
)

enum class MsgType { WELCOME, MATCH_RESULT, TRANSFER, CONTRACT, INJURY, BOARD, YOUTH, FINANCE, SCOUT, JOB, SEASON, NEWS, MEDIA }

@Serializable
data class InboxMessage(
    val id: Long,
    val day: Int,
    val type: MsgType,
    val key: String,
    val args: List<String> = emptyList(),
    var read: Boolean = false,
    /** Optional entity reference (player or club id). */
    val ref: Long = -1,
)

@Serializable
data class TransferRecord(
    val day: Int,
    val playerId: Long,
    val playerName: String,
    val fromClub: Long,
    val toClub: Long,
    val fee: Long,
    val loan: Boolean = false,
    val free: Boolean = false,
)

@Serializable
data class ArchivedPerson(
    val id: Long,
    val name: String,
    val nationId: Int,
    val birthDay: Int,
    val retiredDay: Int,
    val position: Position,
    val peakCa: Int,
    val apps: Int,
    val goals: Int,
    val clubs: List<String>,
)

@Serializable
data class SeasonSummary(
    val season: Int,
    /** compId -> champion club name. */
    val champions: Map<Int, String>,
    val userClub: String,
    val userPosition: Int,
    val topScorer: String,
)

@Serializable
data class CareerEntry(
    val season: Int,
    val clubId: Long,
    val clubName: String,
    val leaguePosition: Int,
    val note: String,
)

@Serializable
class ManagerProfile(
    var firstName: String,
    var lastName: String,
    val nationId: Int,
    val birthDay: Int,
    var qualification: String,
    var playingExperience: String,
    var style: ManagerStyle,
    var reputation: Int,
    var clubId: Long = -1,
    val history: MutableList<CareerEntry> = mutableListOf(),
    val achievements: MutableList<String> = mutableListOf(),
    var jobOffers: MutableList<Long> = mutableListOf(),
    var matches: Int = 0,
    var wins: Int = 0,
    var draws: Int = 0,
    var losses: Int = 0,
    var sackings: Int = 0,
    /** User scouting: playerId -> knowledge 0–100. */
    val knowledge: MutableMap<Long, Int> = linkedMapOf(),
    /** Nation currently assigned to the club's scouts (-1 = none). */
    var scoutingNation: Int = -1,
    val shortlist: MutableList<Long> = mutableListOf(),
) {
    val name get() = "$firstName $lastName".trim()
}

enum class PromiseType(val id: String, val en: String) {
    PLAYING_TIME("Lebih banyak waktu bermain", "More playing time"),
    NEW_CONTRACT("Kontrak baru", "A new contract"),
    SIGN_PLAYER("Rekrut pemain baru di posisinya", "Sign a player for his position"),
    SELL_IF_OFFER("Dijual jika ada tawaran layak", "Sold if a fair offer arrives");
    fun label(lang: Lang) = if (lang == Lang.ID) id else en
}

@Serializable
data class Promise(
    val playerId: Long,
    val type: PromiseType,
    val madeDay: Int,
    val deadlineDay: Int,
    var resolved: Boolean = false,
    var kept: Boolean = false,
    /** Appearances at the time of the promise (for playing-time promises). */
    val appsAtStart: Int = 0,
)

/** One press-conference question waiting for the manager's answer. */
@Serializable
data class PressQuestion(val id: Int, val key: String, val args: List<String> = emptyList(), val playerId: Long = -1)

@Serializable
data class PressConference(val day: Int, val pre: Boolean, val fixtureId: Long, val questions: List<PressQuestion>, var answered: Int = 0)

@Serializable
data class WorldSettings(
    var lang: Lang = Lang.ID,
    var autosave: Boolean = true,
    /** Nations simulated with the reduced (detailed) match model. */
    val detailedNations: MutableList<Int> = mutableListOf(),
)

@Serializable
class World(
    val schemaVersion: Int,
    val seed: Long,
    var today: Int,
    /** Season = year in which the season starts (season 2026 runs Jul 2026 – Jun 2027). */
    var season: Int,
    val nations: List<Nation>,
    val competitions: List<Competition>,
    val clubs: MutableMap<Long, Club>,
    val players: MutableMap<Long, Player>,
    val staff: MutableMap<Long, Staff>,
    val compSeasons: MutableMap<Int, CompSeason>,
    var nextId: Long,
    val manager: ManagerProfile,
    val inbox: MutableList<InboxMessage> = mutableListOf(),
    val news: MutableList<InboxMessage> = mutableListOf(),
    val transfers: MutableList<TransferRecord> = mutableListOf(),
    val archive: MutableList<ArchivedPerson> = mutableListOf(),
    val seasonHistory: MutableList<SeasonSummary> = mutableListOf(),
    val settings: WorldSettings = WorldSettings(),
    /** Target active-player population per nation (keeps the world bounded). */
    val populationTarget: MutableMap<Int, Int> = linkedMapOf(),
    var userTacticFamiliarity: Int = 50,
    val promises: MutableList<Promise> = mutableListOf(),
    var pendingPress: PressConference? = null,
    /** Media hype around the user's club, 0–100 (raises pressure). */
    var mediaHype: Int = 50,
    /** Dressing-room atmosphere, 0–100 (cached weekly). */
    var atmosphere: Int = 60,
) {
    fun newId(): Long = nextId++
    fun nation(id: Int) = nations.first { it.id == id }
    fun comp(id: Int) = competitions.first { it.id == id }
    fun club(id: Long) = clubs.getValue(id)
    fun clubOrNull(id: Long) = clubs[id]
    fun player(id: Long) = players.getValue(id)
    val userClub: Club? get() = clubs[manager.clubId]

    /** Players currently playing for the club (own players not out on loan, plus loanees in). */
    fun squad(clubId: Long): List<Player> = clubs[clubId]?.playerIds?.mapNotNull { players[it] } ?: emptyList()

    fun leagueOf(club: Club): Competition = comp(club.leagueId)
}

@Serializable
enum class Lang { ID, EN }
