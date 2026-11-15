package fm.engine.world

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.competition.CompetitionEngine
import fm.engine.finance.Finance
import fm.engine.market.Valuation

/**
 * Builds the default fictional world: real nations and realistic league structures, but
 * fictional clubs, players and staff. Fully deterministic for a given seed.
 */
object WorldGenerator {
    const val SCHEMA_VERSION = 5

    // Competition ids
    const val IDN_1 = 101; const val IDN_2 = 102; const val IDN_3 = 103; const val IDN_CUP = 110
    const val MAS_1 = 201; const val THA_1 = 301; const val VIE_1 = 401; const val JPN_1 = 501
    const val ASIA_CUP = 900

    private const val M = Money.MILIAR
    private const val JT = Money.JUTA

    val nations = listOf(
        Nation(1, "IDN", "Indonesia", "Indonesia", Continent.ASIA, 45, 10, "id", 12 * JT, 50_000, true),
        Nation(2, "MAS", "Malaysia", "Malaysia", Continent.ASIA, 42, 8, "my", 15 * JT, 40_000, false),
        Nation(3, "THA", "Thailand", "Thailand", Continent.ASIA, 50, 10, "th", 20 * JT, 60_000, false),
        Nation(4, "VIE", "Vietnam", "Vietnam", Continent.ASIA, 46, 10, "vn", 13 * JT, 40_000, false),
        Nation(5, "JPN", "Jepang", "Japan", Continent.ASIA, 72, 15, "jp", 60 * JT, 350_000, false),
        Nation(6, "BRA", "Brasil", "Brazil", Continent.SOUTH_AMERICA, 90, 18, "br", 40 * JT, 100_000, false),
        Nation(7, "KOR", "Korea Selatan", "South Korea", Continent.ASIA, 68, 14, "kr", 55 * JT, 200_000, false),
        Nation(8, "AUS", "Australia", "Australia", Continent.OCEANIA, 62, 12, "au", 60 * JT, 300_000, false),
        Nation(9, "ESP", "Spanyol", "Spain", Continent.EUROPE, 92, 17, "es", 90 * JT, 500_000, false),
        Nation(10, "NGA", "Nigeria", "Nigeria", Continent.AFRICA, 60, 14, "ng", 15 * JT, 30_000, false),
    )

    /** Nations whose players appear abroad as foreign signings. */
    val foreignSources = listOf(6, 7, 8, 9, 10, 5)

    private data class LeagueSpec(val nation: String, val avgCa: Int, val repHi: Int, val repLo: Int)

    private val leagueSpecs = mapOf(
        IDN_1 to LeagueSpec("IDN", 92, 72, 46), IDN_2 to LeagueSpec("IDN", 72, 46, 28),
        IDN_3 to LeagueSpec("IDN", 57, 28, 10), MAS_1 to LeagueSpec("MAS", 90, 64, 40),
        THA_1 to LeagueSpec("THA", 100, 70, 45), VIE_1 to LeagueSpec("VIE", 95, 66, 42),
        JPN_1 to LeagueSpec("JPN", 128, 84, 62),
    )

    val competitions = listOf(
        Competition(IDN_1, "Liga Indonesia 1", "Indonesia League 1", "IDN1", CompType.LEAGUE, 1, 1, 50,
            league = LeagueRules(18, 2, 0, 3, -1, IDN_2, tvMoneyPerClub = 5 * M, championPrize = 3 * M,
                maxForeignInSquad = 6)),
        Competition(IDN_2, "Liga Indonesia 2", "Indonesia League 2", "IDN2", CompType.LEAGUE, 1, 2, 30,
            league = LeagueRules(18, 2, 3, 3, IDN_1, IDN_3, tvMoneyPerClub = 800 * JT, championPrize = 500 * JT,
                maxForeignInSquad = 3)),
        Competition(IDN_3, "Liga Indonesia 3", "Indonesia League 3", "IDN3", CompType.LEAGUE, 1, 3, 15,
            league = LeagueRules(20, 2, 3, 0, IDN_2, -1, tvMoneyPerClub = 150 * JT, championPrize = 100 * JT,
                maxForeignInSquad = 0)),
        Competition(MAS_1, "Liga Malaysia", "Malaysia League", "MAS1", CompType.LEAGUE, 2, 1, 45,
            league = LeagueRules(14, 2, tvMoneyPerClub = 4 * M, championPrize = 2 * M)),
        Competition(THA_1, "Liga Thailand", "Thailand League", "THA1", CompType.LEAGUE, 3, 1, 52,
            league = LeagueRules(16, 2, tvMoneyPerClub = 7 * M, championPrize = 4 * M)),
        Competition(VIE_1, "Liga Vietnam", "Vietnam League", "VIE1", CompType.LEAGUE, 4, 1, 47,
            league = LeagueRules(14, 2, tvMoneyPerClub = 4 * M, championPrize = 2 * M)),
        Competition(JPN_1, "Liga Jepang", "Japan League", "JPN1", CompType.LEAGUE, 5, 1, 70,
            league = LeagueRules(18, 2, tvMoneyPerClub = 40 * M, championPrize = 25 * M)),
        Competition(IDN_CUP, "Piala Indonesia", "Indonesia Cup", "PIALA", CompType.CUP, 1, 0, 35,
            cup = CupRules(entrantLeagues = listOf(IDN_1, IDN_2, IDN_3), matchDow = 3,
                roundDates = listOf(intArrayOf(9, 16), intArrayOf(10, 21), intArrayOf(11, 25), intArrayOf(2, 10), intArrayOf(3, 17), intArrayOf(4, 21)),
                winnerPrize = 3 * M, participationPrize = 50 * JT)),
        Competition(ASIA_CUP, "Piala Juara Asia", "Asian Champions Cup", "PJA", CompType.CONTINENTAL, -1, 0, 65,
            cup = CupRules(qualification = listOf(intArrayOf(5, 4), intArrayOf(3, 3), intArrayOf(1, 3), intArrayOf(2, 3), intArrayOf(4, 3)),
                twoLegged = true, singleLegFinal = true, matchDow = 2,
                roundDates = listOf(intArrayOf(10, 6), intArrayOf(10, 20), intArrayOf(2, 2), intArrayOf(2, 16), intArrayOf(3, 9), intArrayOf(3, 23), intArrayOf(5, 18)),
                winnerPrize = 20 * M, participationPrize = 1 * M)),
    )

    /** Squad template: positions of a generated 26-man squad (first 11 are the strongest). */
    private val squadTemplate = listOf(
        Position.GK, Position.DR, Position.DC, Position.DC, Position.DL, Position.DM, Position.MC, Position.MC,
        Position.AML, Position.AMR, Position.ST,
        Position.GK, Position.DR, Position.DL, Position.DC, Position.DC, Position.DM, Position.MC, Position.MC,
        Position.ML, Position.MR, Position.AMC, Position.AMC, Position.ST, Position.ST, Position.GK,
    )

    private val palette = listOf(0xFFD32F2F, 0xFF1976D2, 0xFF388E3C, 0xFFFBC02D, 0xFF7B1FA2, 0xFFF57C00, 0xFF212121,
        0xFF0097A7, 0xFFC2185B, 0xFF5D4037, 0xFF455A64, 0xFFFFFFFF, 0xFF303F9F, 0xFF689F38)

    fun generate(seed: Long, manager: ManagerProfile, startYear: Int = 2026): World {
        val world = World(
            schemaVersion = SCHEMA_VERSION, seed = seed, today = Cal.of(startYear, 7, 1), season = startYear,
            nations = nations, competitions = competitions,
            clubs = linkedMapOf(), players = linkedMapOf(), staff = linkedMapOf(), compSeasons = linkedMapOf(),
            nextId = 1000, manager = manager,
        )
        world.settings.detailedNations.addAll(nations.filter { it.playable }.map { it.id })
        val rng = Rng.stream(seed, "worldgen")
        val usedCities = HashMap<String, MutableList<String>>()

        for ((compId, spec) in leagueSpecs) {
            val comp = competitions.first { it.id == compId }
            val nation = nations.first { it.code == spec.nation }
            val cities = usedCities.getOrPut(spec.nation) { rng.shuffled(NamePools.cities.getValue(spec.nation)).toMutableList() }
            val n = comp.league!!.teams
            for (i in 0 until n) {
                val rep = (spec.repHi - (spec.repHi - spec.repLo) * i / (n - 1).coerceAtLeast(1) + rng.range(-3, 3)).coerceIn(1, 100)
                val city = cities.removeAt(0)
                val club = makeClub(world, rng, nation, comp, city, rep)
                world.clubs[club.id] = club
                val clubCa = spec.avgCa + (rep - (spec.repHi + spec.repLo) / 2) * 0.55
                fillSquad(world, rng, club, nation, comp, clubCa)
                hireStaff(world, rng, club, nation)
                initFinances(world, rng, club)
            }
        }
        // Unemployed managers and free agents give the market some liquidity from day one.
        repeat(24) {
            val nation = nations[rng.nextInt(5)]
            val s = Factory.staff(world, rng, nation, StaffRole.MANAGER, rng.range(6.0, 13.0), rng.range(35, 62))
            world.staff[s.id] = s
        }
        repeat(60) {
            val nation = nations[rng.nextInt(nations.size)]
            val pos = rng.pick(squadTemplate)
            val p = Factory.player(world, rng, nation, pos, rng.range(45, 110), rng.range(21, 32))
            Factory.refreshValue(world, p)
            world.players[p.id] = p
        }
        for (nation in nations) world.populationTarget[nation.id] = world.players.values.count { it.nationId == nation.id }
        CompetitionEngine.startSeason(world)
        return world
    }

    private fun makeClub(world: World, rng: Rng, nation: Nation, comp: Competition, city: String, rep: Int): Club {
        val name = rng.pick(NamePools.clubPatterns.getValue(nation.code)).replace("{c}", city)
        val short = city.take(3).uppercase()
        val capacity = (4000 + rep * rep * 6 + rng.range(-1500, 3000)).coerceIn(3000, 70000)
        val c1 = rng.pick(palette)
        var c2 = rng.pick(palette)
        if (c2 == c1) c2 = 0xFFFFFFFF
        return Club(
            id = world.newId(), nationId = nation.id, name = name, shortName = short, city = city, reputation = rep,
            stadiumName = rng.pick(NamePools.stadiumPatterns).replace("{c}", city), stadiumCapacity = capacity,
            trainingFacilities = (rep / 6 + rng.range(1, 6)).coerceIn(1, 20),
            youthFacilities = (rep / 6 + rng.range(1, 6)).coerceIn(1, 20),
            youthRecruitment = (rep / 6 + rng.range(1, 6)).coerceIn(1, 20),
            primaryColor = c1, secondaryColor = c2, founded = rng.range(1925, 2012),
            balance = 0, transferBudget = 0, wageBudget = 0, leagueId = comp.id,
            fanBase = (rep * rep * 5 + rng.range(500, 4000)).coerceAtLeast(800),
        )
    }

    private fun fillSquad(world: World, rng: Rng, club: Club, nation: Nation, comp: Competition, clubCa: Double) {
        val foreignSlots = when {
            comp.league!!.maxForeignInSquad == 0 -> 0
            nation.code == "JPN" -> 3
            comp.tier == 1 -> 4
            else -> 2
        }
        val foreignIdx = (0 until 11).filter { squadTemplate[it] != Position.GK }.let { rng.shuffled(it).take(foreignSlots) }.toSet()
        squadTemplate.forEachIndexed { i, pos ->
            val starter = i < 11
            val foreign = i in foreignIdx
            val pNation = if (foreign) world.nation(rng.pick(foreignSources.filter { it != nation.id })) else nation
            val age = if (starter) rng.range(21, 33) else rng.range(18, 34)
            val target = clubCa + (if (starter) 5.0 else -6.0) + (if (foreign) 12.0 else 0.0) + rng.gaussian() * 7.0 -
                (if (age <= 20) (21 - age) * 4.0 else 0.0)
            val p = Factory.player(world, rng, pNation, pos, target.toInt().coerceIn(25, 185), age)
            world.players[p.id] = p
            val years = rng.range(1, 4)
            val wage = Money.scale(Valuation.wage(p.ca, nation, club.reputation), rng.range(0.85, 1.15))
            val status = when {
                starter && i < 4 -> SquadStatus.KEY
                starter -> SquadStatus.FIRST_TEAM
                age <= 20 -> SquadStatus.PROSPECT
                i < 20 -> SquadStatus.ROTATION
                else -> SquadStatus.BACKUP
            }
            Factory.signContract(world, p, club, years, wage, status)
            p.joinedDay = world.today - rng.range(30, 1500)
            Factory.refreshValue(world, p)
        }
    }

    private val staffRoles = listOf(StaffRole.MANAGER, StaffRole.ASSISTANT, StaffRole.COACH, StaffRole.COACH,
        StaffRole.GK_COACH, StaffRole.FITNESS_COACH, StaffRole.SCOUT, StaffRole.PHYSIO, StaffRole.YOUTH_COACH)

    private fun hireStaff(world: World, rng: Rng, club: Club, nation: Nation) {
        val level = 5.0 + club.reputation / 9.0
        for (role in staffRoles) {
            val s = Factory.staff(world, rng, nation, role, level, rng.range(32, 64))
            val wage = staffWage(nation, role, s.quality())
            s.clubId = club.id
            s.contract = Contract(club.id, world.today, world.season + rng.range(1, 3), wage)
            world.staff[s.id] = s
            club.staffIds.add(s.id)
            if (role == StaffRole.MANAGER) {
                club.managerId = s.id
                club.tactic = Tactic(formation = Formation.byName(s.style.preferredFormation), instructions = styleInstructions(s.style))
            }
        }
    }

    fun staffWage(nation: Nation, role: StaffRole, quality: Double): Long {
        val mult = if (role == StaffRole.MANAGER) 3.0 else 0.8
        return (Math.round(nation.wageIndex * mult * (0.5 + quality / 10.0) / 500_000) * 500_000).coerceAtLeast(1_000_000)
    }

    fun styleInstructions(style: ManagerStyle) = TeamInstructions(
        mentality = when {
            style.attacking > 0.6 -> Mentality.ATTACKING
            style.attacking > 0.2 -> Mentality.POSITIVE
            style.attacking < -0.5 -> Mentality.DEFENSIVE
            else -> Mentality.BALANCED
        },
        directness = style.directness,
        pressing = style.pressing,
        engagement = 0.3 + style.pressing * 0.5,
        defensiveLine = 0.3 + style.pressing * 0.5,
        tempo = 0.35 + style.directness * 0.4,
        counterPress = style.pressing,
        counterAttack = 0.3 + style.directness * 0.5,
    )

    private fun initFinances(world: World, rng: Rng, club: Club) {
        val wages = Finance.playerWageBill(world, club) + Finance.staffWageBill(world, club)
        val league = world.comp(club.leagueId).league!!
        val target = wages * 12 * 1.12
        val homeGames = (league.teams - 1) * league.rounds / 2
        val matchday = homeGames * (club.fanBase * 0.7).toInt().coerceAtMost(club.stadiumCapacity).toLong() *
            world.nation(club.nationId).ticketPrice
        club.commercialBase = maxOf((target * 0.15).toLong(), (target - league.tvMoneyPerClub - matchday).toLong())
        club.commercialRepRef = club.reputation
        club.balance = Money.scale(wages, rng.range(3.0, 9.0))
        club.finance.openingBalance = club.balance
        Finance.setBudgets(world, club)
        club.expectedPosition = 0 // computed after all clubs exist (see Board.setExpectations)
    }

    /** Recomputes cached CA for every player (used after load/migrations). */
    fun recomputeAll(world: World) = world.players.values.forEach { Ability.recompute(it) }
}
