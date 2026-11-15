package fm.engine.data

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.finance.Finance
import fm.engine.market.Valuation
import fm.engine.world.Factory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * User-supplied database packs (JSON, or CSV for players). Packs rename and re-populate the
 * clubs of the default world so players can use data they have the right to use. Nothing is
 * downloaded or shipped: the user imports a file. Packs are validated before use.
 */
@Serializable
data class PackClub(
    /** Competition short code, e.g. "IDN1", "IDN2", "IDN3", "MAS1", "THA1", "VIE1", "JPN1". */
    val league: String,
    val name: String,
    val shortName: String? = null,
    val city: String? = null,
    val reputation: Int? = null,
    val stadium: String? = null,
    val capacity: Int? = null,
    /** "#RRGGBB" */
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
)

@Serializable
data class PackPlayer(
    /** Club name exactly as in the pack/world; empty = free agent. */
    val club: String = "",
    val firstName: String = "",
    val lastName: String,
    /** Nation code, e.g. "IDN". */
    val nation: String,
    /** ISO date YYYY-MM-DD. */
    val birthDate: String,
    /** Position code: GK DR DC DL DM MR MC ML AMR AMC AML ST. */
    val position: String,
    val foot: String? = null,
    val heightCm: Int? = null,
    /** Target current ability 1–200 (used when attributes are not given). */
    val ability: Int? = null,
    val potential: Int? = null,
    /** Attribute name (e.g. "FINISHING") → 1..20. Missing attributes are generated. */
    val attributes: Map<String, Int> = emptyMap(),
    val wageMonthly: Long? = null,
    /** Contract end year (30 June). */
    val contractEnd: Int? = null,
)

@Serializable
data class DatabasePack(
    val formatVersion: Int = 1,
    val name: String,
    val author: String = "",
    val clubs: List<PackClub> = emptyList(),
    val players: List<PackPlayer> = emptyList(),
)

object Packs {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }

    data class Issue(val error: Boolean, val message: String)

    fun parseJson(text: String): DatabasePack = json.decodeFromString(DatabasePack.serializer(), text)
    fun toJson(pack: DatabasePack): String = json.encodeToString(DatabasePack.serializer(), pack)

    /** CSV of players with a header row. Required columns: lastName,nation,birthDate,position. */
    fun parsePlayersCsv(text: String, packName: String = "CSV"): DatabasePack {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return DatabasePack(name = packName)
        val header = splitCsv(lines[0]).map { it.trim() }
        val attrNames = Attr.entries.map { it.name }.toSet()
        val players = lines.drop(1).map { line ->
            val cells = splitCsv(line)
            val m = header.indices.associate { header[it] to cells.getOrElse(it) { "" }.trim() }
            PackPlayer(
                club = m["club"] ?: "", firstName = m["firstName"] ?: "", lastName = m["lastName"] ?: "",
                nation = m["nation"] ?: "", birthDate = m["birthDate"] ?: "", position = m["position"] ?: "",
                foot = m["foot"]?.ifBlank { null }, heightCm = m["heightCm"]?.toIntOrNull(),
                ability = m["ability"]?.toIntOrNull(), potential = m["potential"]?.toIntOrNull(),
                attributes = m.filterKeys { it.uppercase() in attrNames }.mapNotNull { (k, v) -> v.toIntOrNull()?.let { k.uppercase() to it } }.toMap(),
                wageMonthly = m["wageMonthly"]?.toLongOrNull(), contractEnd = m["contractEnd"]?.toIntOrNull(),
            )
        }
        return DatabasePack(name = packName, players = players)
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false
        for (ch in line) when {
            ch == '"' -> q = !q
            ch == ',' && !q -> { out += sb.toString(); sb.clear() }
            else -> sb.append(ch)
        }
        out += sb.toString()
        return out
    }

    private fun color(s: String?): Long? = s?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

    /** Validates a pack against the default world structure. Errors block the import; warnings don't. */
    fun validate(pack: DatabasePack, world: World): List<Issue> {
        val issues = mutableListOf<Issue>()
        if (pack.formatVersion > 1) issues += Issue(true, "Unsupported formatVersion ${pack.formatVersion}")
        val leagues = world.competitions.filter { it.type == CompType.LEAGUE }.associateBy { it.shortName }
        val names = HashSet<String>()
        for ((i, c) in pack.clubs.withIndex()) {
            val where = "club #${i + 1} '${c.name}'"
            if (c.league !in leagues) issues += Issue(true, "$where: unknown league '${c.league}' (use ${leagues.keys.joinToString()})")
            if (c.name.isBlank()) issues += Issue(true, "$where: empty name")
            if (!names.add(c.name.lowercase())) issues += Issue(true, "$where: duplicate club name")
            if (c.reputation != null && c.reputation !in 1..100) issues += Issue(true, "$where: reputation must be 1–100")
            if (c.capacity != null && c.capacity !in 500..120_000) issues += Issue(false, "$where: unusual capacity ${c.capacity}")
            if (c.primaryColor != null && color(c.primaryColor) == null) issues += Issue(false, "$where: bad colour '${c.primaryColor}'")
        }
        for ((code, comp) in leagues) {
            val n = pack.clubs.count { it.league == code }
            val places = comp.league!!.teams
            if (n > places) issues += Issue(true, "League $code has $places places but the pack lists $n clubs")
        }
        val clubNames = pack.clubs.map { it.name.lowercase() }.toSet() + world.clubs.values.map { it.name.lowercase() }
        val nations = world.nations.map { it.code }.toSet()
        val attrNames = Attr.entries.map { it.name }.toSet()
        for ((i, p) in pack.players.withIndex()) {
            val where = "player #${i + 1} '${"${p.firstName} ${p.lastName}".trim()}'"
            if (p.lastName.isBlank()) issues += Issue(true, "$where: lastName is required")
            if (p.nation !in nations) issues += Issue(true, "$where: unknown nation '${p.nation}' (use ${nations.joinToString()})")
            if (Position.entries.none { it.code == p.position }) issues += Issue(true, "$where: unknown position '${p.position}'")
            val dob = runCatching { LocalDate.parse(p.birthDate) }.getOrNull()
            if (dob == null) issues += Issue(true, "$where: birthDate must be YYYY-MM-DD")
            else { val age = Cal.year(world.today) - dob.year; if (age !in 14..45) issues += Issue(true, "$where: age $age out of range 14–45") }
            if (p.club.isNotBlank() && p.club.lowercase() !in clubNames) issues += Issue(true, "$where: unknown club '${p.club}'")
            for ((k, v) in p.attributes) {
                if (k.uppercase() !in attrNames) issues += Issue(false, "$where: unknown attribute '$k' ignored")
                else if (v !in 1..20) issues += Issue(true, "$where: attribute $k=$v must be 1–20")
            }
            if (p.ability != null && p.ability !in 1..200) issues += Issue(true, "$where: ability must be 1–200")
            if (p.potential != null && p.potential !in 1..200) issues += Issue(true, "$where: potential must be 1–200")
            if (p.ability == null && p.attributes.isEmpty()) issues += Issue(false, "$where: no ability or attributes; a typical level for the club is generated")
        }
        return issues
    }

    data class ApplyReport(val clubsRenamed: Int, val playersCreated: Int, val playersGeneratedToFill: Int)

    /** Applies a validated pack to a freshly generated world (before the first day is played). */
    fun apply(pack: DatabasePack, world: World): ApplyReport {
        require(validate(pack, world).none { it.error }) { "Pack has errors" }
        var renamed = 0; var created = 0; var filled = 0
        // 1. Clubs: pack club i of a league replaces the i-th generated club of that league by reputation.
        for (comp in world.competitions.filter { it.type == CompType.LEAGUE }) {
            val packClubs = pack.clubs.filter { it.league == comp.shortName }.sortedByDescending { it.reputation ?: 0 }
            val slots = world.clubs.values.filter { it.leagueId == comp.id }.sortedByDescending { it.reputation }
            for ((pc, club) in packClubs.zip(slots)) {
                club.name = pc.name
                club.shortName = (pc.shortName ?: pc.name.filter { it.isLetter() }.take(3)).uppercase().take(4)
                pc.reputation?.let { club.reputation = it; club.commercialRepRef = it }
                pc.capacity?.let { club.stadiumCapacity = it }
                renamed++
                color(pc.primaryColor)?.let { club.primaryColor = it }
                color(pc.secondaryColor)?.let { club.secondaryColor = it }
                pc.stadium?.let { club.stadiumName = it }
                pc.city?.let { club.city = it }
            }
        }
        // 2. Players: clubs with pack players get exactly those players (topped up to 20).
        val byClub = pack.players.groupBy { it.club.lowercase() }
        val rng = Rng.stream(world.seed, "packapply")
        for (club in world.clubs.values.sortedBy { it.id }) {
            val list = byClub[club.name.lowercase()] ?: continue
            val clubLevel = world.squad(club.id).map { it.ca }.average().takeIf { !it.isNaN() } ?: 70.0
            for (p in world.squad(club.id).toList()) { club.playerIds.remove(p.id); world.players.remove(p.id) }
            for (pp in list) { create(world, rng, pp, club, clubLevel); created++ }
            while (world.squad(club.id).size < 20) {
                val pos = rng.pick(Position.entries)
                val p = Factory.player(world, rng, world.nation(club.nationId), pos, (clubLevel - 8).toInt(), rng.range(18, 30))
                world.players[p.id] = p
                Factory.signContract(world, p, club, rng.range(1, 3), Valuation.wage(p.ca, world.nation(club.nationId), club.reputation), SquadStatus.ROTATION)
                Factory.refreshValue(world, p)
                filled++
            }
        }
        for (pp in byClub[""].orEmpty()) { create(world, rng, pp, null, 70.0); created++ }
        // 3. Finances follow the new wage bills; population targets follow the new player counts.
        for (club in world.clubs.values) rebalanceFinances(world, club)
        for (n in world.nations) world.populationTarget[n.id] = world.players.values.count { it.nationId == n.id }.coerceAtLeast(10)
        return ApplyReport(renamed, created, filled)
    }

    private fun create(world: World, rng: Rng, pp: PackPlayer, club: Club?, clubLevel: Double) {
        val nation = world.nations.first { it.code == pp.nation }
        val pos = Position.entries.first { it.code == pp.position }
        val dob = LocalDate.parse(pp.birthDate)
        val age = (Cal.year(world.today) - dob.year).coerceIn(14, 45)
        val target = pp.ability ?: clubLevel.toInt()
        val p = Factory.player(world, rng, nation, pos, target, age)
        val attrs = p.attrs
        for ((k, v) in pp.attributes) Attr.entries.firstOrNull { it.name == k.uppercase() }?.let { attrs[it.ordinal] = (v.coerceIn(1, 20) * 100).toShort() }
        val made = Player(p.id, pp.firstName, pp.lastName, nation.id, dob.toEpochDay().toInt(), pos, p.secondary,
            when (pp.foot?.uppercase()) { "LEFT", "L", "KIRI" -> Foot.LEFT; "BOTH", "B", "KEDUANYA" -> Foot.BOTH; else -> Foot.RIGHT },
            pp.heightCm ?: p.heightCm, p.weightKg, attrs, 0, p.personality, p.injuryProneness)
        Ability.recompute(made)
        made.pa = (pp.potential ?: maxOf(p.pa, made.ca)).coerceAtLeast(made.ca)
        made.morale = 70
        world.players[made.id] = made
        if (club != null) {
            val wage = pp.wageMonthly ?: Valuation.wage(made.ca, world.nation(club.nationId), club.reputation)
            val years = ((pp.contractEnd ?: (world.season + 2)) - world.season).coerceIn(1, 5)
            Factory.signContract(world, made, club, years, wage, SquadStatus.ROTATION)
        }
        Factory.refreshValue(world, made)
    }

    fun rebalanceFinances(world: World, club: Club) {
        val wages = Finance.playerWageBill(world, club) + Finance.staffWageBill(world, club)
        val league = world.comp(club.leagueId).league ?: return
        val target = wages * 12 * 1.12
        val homeGames = (league.teams - 1) * league.rounds / 2
        val matchday = homeGames * (club.fanBase * 0.7).toInt().coerceAtMost(club.stadiumCapacity).toLong() * world.nation(club.nationId).ticketPrice
        club.commercialBase = maxOf((target * 0.15).toLong(), (target - league.tvMoneyPerClub - matchday).toLong())
        club.commercialRepRef = club.reputation
        Finance.setBudgets(world, club)
    }

    /** Exports the current world's clubs and players as an editable pack. */
    fun export(world: World, name: String): DatabasePack {
        fun hex(v: Long) = "#%06X".format(v and 0xFFFFFF)
        val clubs = world.clubs.values.filter { world.comp(it.leagueId).type == CompType.LEAGUE }.map { c ->
            PackClub(world.comp(c.leagueId).shortName, c.name, c.shortName, c.city, c.reputation, c.stadiumName, c.stadiumCapacity, hex(c.primaryColor), hex(c.secondaryColor))
        }
        val players = world.players.values.map { p ->
            PackPlayer(world.clubs[p.clubId]?.name ?: "", p.firstName, p.lastName, world.nation(p.nationId).code, Cal.date(p.birthDay).toString(),
                p.position.code, p.foot.name, p.heightCm, p.ca, p.pa, Attr.entries.associate { it.name to p.attrInt(it) },
                p.contract?.wage, p.contract?.endYear)
        }
        return DatabasePack(1, name, "export", clubs, players)
    }
}
