package fm.engine.world

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.market.Valuation

/** Creates individual players and staff with deterministic randomness. */
object Factory {
    fun personality(rng: Rng, bias: Int = 0) = Personality(
        professionalism = attr20(rng, 11.0 + bias),
        ambition = attr20(rng, 11.0),
        pressure = attr20(rng, 11.0),
        temperament = attr20(rng, 12.0),
        loyalty = attr20(rng, 11.0),
        adaptability = attr20(rng, 11.0),
        consistency = attr20(rng, 11.0),
        bigMatches = attr20(rng, 11.0),
    )

    private fun attr20(rng: Rng, mean: Double) = Math.round(rng.gaussian(mean, 3.8)).toInt().coerceIn(1, 20)

    private val ageSensitivePhysical = setOf(Attr.PACE, Attr.ACCELERATION, Attr.STAMINA, Attr.AGILITY, Attr.NATURAL_FITNESS)
    private val experienceMental = setOf(Attr.DECISIONS, Attr.COMPOSURE, Attr.ANTICIPATION, Attr.POSITIONING, Attr.CONCENTRATION, Attr.LEADERSHIP)
    private val gkUsefulOutfield = setOf(Attr.PASSING, Attr.FIRST_TOUCH, Attr.KICKING)

    /** Attributes for a player of [pos] whose CA in that position is close to [targetCa]. */
    fun attributes(rng: Rng, pos: Position, targetCa: Int, age: Int): ShortArray {
        val w = Ability.weights(pos)
        val maxW = w.max()
        val targetAvg = 1.0 + targetCa / 200.0 * 19.0
        val v = DoubleArray(Attr.COUNT)
        for (a in Attr.entries) {
            val imp = w[a.ordinal] / maxW
            var x = targetAvg + (imp - 0.4) * 3.5 + rng.gaussian() * 1.7
            if (a.group == AttrGroup.GOALKEEPING && pos != Position.GK) x = 1.0 + rng.nextDouble() * 3.5
            if (pos == Position.GK && a.group == AttrGroup.TECHNICAL && a !in gkUsefulOutfield) x = targetAvg * 0.45 + rng.gaussian()
            if (a in ageSensitivePhysical) {
                if (age < 19) x -= 1.2
                if (age > 30) x -= (age - 30) * 0.45
            }
            if (a == Attr.STRENGTH && age < 20) x -= (20 - age) * 0.6
            if (a in experienceMental) {
                if (age < 20) x -= 1.0
                if (age > 28) x += 0.6
            }
            v[a.ordinal] = x
        }
        repeat(6) {
            val arr = toShorts(v)
            val avg = Ability.weightedAverage(arr, pos)
            val delta = targetAvg - avg
            for (i in v.indices) v[i] += if (w[i] > 0) delta else delta * 0.5
        }
        return toShorts(v)
    }

    private fun toShorts(v: DoubleArray) = ShortArray(v.size) { (Math.round(v[it].coerceIn(1.0, 20.0) * 100)).toInt().toShort() }

    private val secondaryOptions = mapOf(
        Position.DC to listOf(Position.DM, Position.DR, Position.DL),
        Position.DR to listOf(Position.MR, Position.DC, Position.DL),
        Position.DL to listOf(Position.ML, Position.DC, Position.DR),
        Position.DM to listOf(Position.MC, Position.DC),
        Position.MC to listOf(Position.DM, Position.AMC, Position.MR, Position.ML),
        Position.MR to listOf(Position.AMR, Position.DR, Position.MC),
        Position.ML to listOf(Position.AML, Position.DL, Position.MC),
        Position.AMC to listOf(Position.MC, Position.ST, Position.AMR, Position.AML),
        Position.AMR to listOf(Position.MR, Position.AML, Position.ST),
        Position.AML to listOf(Position.ML, Position.AMR, Position.ST),
        Position.ST to listOf(Position.AMC, Position.AMR, Position.AML),
        Position.GK to emptyList(),
    )

    fun name(rng: Rng, pool: NamePools.Pool): Pair<String, String> {
        val first = rng.pick(pool.first)
        val last = rng.pick(pool.last)
        if (pool.singleNameChance > 0 && rng.chance(pool.singleNameChance)) return "" to first
        return first to last
    }

    fun player(
        world: World, rng: Rng, nation: Nation, pos: Position, targetCa: Int, age: Int,
        pa: Int? = null, birthYearRef: Int = Cal.year(world.today),
    ): Player {
        val (fn, ln) = name(rng, NamePools.pool(nation.namePool))
        // Exactly [age] years old today (birthday spread across the year).
        val birthDay = world.today - Math.round(age * 365.25).toInt() - rng.range(1, 360) + (birthYearRef - Cal.year(world.today)) * 0
        val attrs = attributes(rng, pos, targetCa, age)
        val sec = secondaryOptions.getValue(pos).filter { rng.chance(0.35) }.take(2)
        val height = when (pos) {
            Position.GK -> rng.range(182, 196)
            Position.DC -> rng.range(178, 193)
            Position.ST -> rng.range(172, 190)
            else -> rng.range(165, 184)
        } - (if (nation.namePool in setOf("id", "vn", "th", "my")) 4 else 0)
        val ca = Ability.caAt(attrs, pos)
        val potential = pa ?: run {
            val head = when {
                age <= 18 -> rng.gaussian(45.0, 22.0)
                age <= 20 -> rng.gaussian(30.0, 16.0)
                age <= 23 -> rng.gaussian(16.0, 10.0)
                age <= 26 -> rng.gaussian(6.0, 5.0)
                else -> rng.gaussian(1.0, 2.0)
            }
            (ca + head.coerceAtLeast(0.0)).toInt()
        }
        val p = Player(
            id = world.newId(), firstName = fn, lastName = ln, nationId = nation.id, birthDay = birthDay,
            position = pos, secondary = sec,
            foot = when (rng.nextInt(10)) { in 0..6 -> Foot.RIGHT; in 7..8 -> Foot.LEFT; else -> Foot.BOTH },
            heightCm = height, weightKg = (height - 105 + rng.range(-6, 8)).coerceIn(55, 100),
            attrs = attrs, pa = potential.coerceIn(ca, 200), personality = personality(rng),
            injuryProneness = Math.round(rng.gaussian(9.0, 4.0)).toInt().coerceIn(1, 20),
        )
        p.ca = ca
        p.condition = 100
        p.morale = rng.range(60, 80)
        p.reputation = (ca / 2 + rng.range(-5, 5)).coerceIn(1, 100)
        return p
    }

    fun signContract(world: World, p: Player, club: Club, years: Int, wage: Long, status: SquadStatus, youth: Boolean = false) {
        p.contract = Contract(club.id, world.today, world.season + years, wage, squadStatus = status, youth = youth)
        p.clubId = club.id
        p.joinedDay = world.today
        if (p.id !in club.playerIds) club.playerIds.add(p.id)
    }

    fun refreshValue(world: World, p: Player) {
        val club = world.clubs[p.clubId]
        val nation = if (club != null) world.nation(club.nationId) else world.nation(p.nationId)
        p.value = Valuation.value(p, nation, club?.reputation ?: 30, world.today, world.season)
    }

    fun staff(world: World, rng: Rng, nation: Nation, role: StaffRole, level: Double, age: Int): Staff {
        val (fn, ln) = name(rng, NamePools.pool(nation.namePool))
        fun a(bonus: Double = 0.0) = Math.round(rng.gaussian(level + bonus, 2.5)).toInt().coerceIn(1, 20)
        val formations = Formation.ALL.map { it.name }
        return Staff(
            id = world.newId(), firstName = fn, lastName = ln, nationId = nation.id,
            birthDay = Cal.of(Cal.year(world.today) - age, 1, 1) - rng.range(0, 364),
            role = role,
            coaching = a(if (role in setOf(StaffRole.COACH, StaffRole.FITNESS_COACH, StaffRole.GK_COACH)) 1.5 else 0.0),
            tactical = a(if (role == StaffRole.MANAGER || role == StaffRole.ASSISTANT) 1.5 else 0.0),
            judgingAbility = a(if (role == StaffRole.SCOUT) 2.0 else 0.0),
            judgingPotential = a(if (role == StaffRole.SCOUT) 2.0 else 0.0),
            motivation = a(), discipline = a(),
            medical = a(if (role == StaffRole.PHYSIO) 3.0 else -3.0),
            youthWork = a(if (role == StaffRole.YOUTH_COACH) 2.0 else 0.0),
            reputation = (level * 5 + rng.range(-8, 8)).toInt().coerceIn(1, 100),
            style = ManagerStyle(
                preferredFormation = rng.pick(formations),
                attacking = rng.range(-0.8, 0.9),
                directness = rng.nextDouble(),
                pressing = rng.nextDouble(),
                youthTrust = rng.nextDouble(),
                riskTolerance = rng.nextDouble(),
            ),
        )
    }
}
