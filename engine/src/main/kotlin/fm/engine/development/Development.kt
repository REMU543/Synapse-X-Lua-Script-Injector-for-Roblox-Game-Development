package fm.engine.development

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.world.Factory
import fm.engine.world.NamePools
import kotlin.math.pow

/**
 * Player development, ageing, injuries, youth intake and retirement.
 * Potential is a ceiling, not a promise: growth needs training quality, playing time and
 * professionalism, and decline starts in the late twenties (physical first, mental last).
 */
object Development {
    private val physical = Attr.entries.filter { it.group == AttrGroup.PHYSICAL }

    private fun focusBoost(focus: TrainingFocus, a: Attr): Double = when (focus) {
        TrainingFocus.BALANCED -> 1.0
        TrainingFocus.ATTACKING -> if (a in setOf(Attr.FINISHING, Attr.DRIBBLING, Attr.OFF_THE_BALL, Attr.CROSSING, Attr.FIRST_TOUCH, Attr.COMPOSURE)) 1.6 else 0.85
        TrainingFocus.DEFENDING -> if (a in setOf(Attr.TACKLING, Attr.MARKING, Attr.POSITIONING, Attr.HEADING, Attr.CONCENTRATION, Attr.ANTICIPATION)) 1.6 else 0.85
        TrainingFocus.PHYSICAL -> if (a.group == AttrGroup.PHYSICAL) 1.7 else 0.85
        TrainingFocus.TECHNICAL -> if (a.group == AttrGroup.TECHNICAL) 1.45 else 0.85
        TrainingFocus.TACTICAL -> if (a in setOf(Attr.DECISIONS, Attr.TEAMWORK, Attr.POSITIONING, Attr.VISION, Attr.WORK_RATE)) 1.5 else 0.85
        TrainingFocus.MATCH_PREP -> 0.8
    }

    fun coachQuality(world: World, club: Club?, gk: Boolean): Double {
        if (club == null) return 6.0
        val coaches = club.staffIds.mapNotNull { world.staff[it] }.filter {
            if (gk) it.role == StaffRole.GK_COACH else it.role == StaffRole.COACH || it.role == StaffRole.FITNESS_COACH || it.role == StaffRole.ASSISTANT
        }
        return if (coaches.isEmpty()) 5.0 else coaches.map { it.coaching.toDouble() }.average()
    }

    /** Weekly training & development tick (every Monday). */
    fun weekly(world: World) {
        val today = world.today
        val coachCache = HashMap<Long, DoubleArray>()
        for (p in world.players.values) {
            val club = world.clubs[p.playingFor]
            val q = coachCache.getOrPut(p.playingFor) { doubleArrayOf(coachQuality(world, club, false), coachQuality(world, club, true)) }
            val coach = if (p.position == Position.GK) q[1] else q[0]
            develop(world, p, club, coach, today)
        }
    }

    fun develop(world: World, p: Player, club: Club?, coach: Double, today: Int) {
        val age = Cal.ageExact(p.birthDay, today)
        val rng = Rng.stream(world.seed, "dev", p.id, today.toLong())
        val headroom = (p.pa - p.ca).coerceAtLeast(0)
        val ageF = when {
            age < 18 -> 1.0
            age < 21 -> 0.95
            age < 24 -> 0.65
            age < 27 -> 0.3
            age < 29 -> 0.08
            else -> 0.0
        }
        val facilities = (club?.trainingFacilities ?: 4) / 20.0
        val train = 0.45 + 0.3 * facilities + 0.25 * coach / 20.0
        val gamesShare = (p.season.minutes / (90.0 * weeksIntoSeason(world).coerceAtLeast(1))).coerceIn(0.0, 1.0)
        val play = if (age < 19) 0.75 + 0.35 * gamesShare else 0.55 + 0.6 * gamesShare
        val prof = 0.55 + 0.9 * p.personality.professionalism / 20.0
        val intensity = (club?.trainingIntensity ?: 2) / 2.0
        val injured = if (p.injuryDays > 0) 0.3 else 1.0
        val growthCa = 0.45 * (headroom / 35.0).coerceAtMost(1.6) * ageF * train * play * prof * injured *
            (0.85 + 0.15 * intensity) * (0.6 + 0.8 * rng.nextDouble())
        val declineCa = if (age > 29.0) 0.035 * (age - 29.0).pow(1.35) * (1.25 - 0.5 * p.attr(Attr.NATURAL_FITNESS) / 20.0) * (0.6 + 0.8 * rng.nextDouble()) else 0.0
        val netAvg = (growthCa - declineCa) * 19.0 / 200.0
        if (netAvg == 0.0) return
        val w = Ability.weights(p.position)
        val focus = club?.trainingFocus ?: TrainingFocus.BALANCED
        for (a in Attr.entries) {
            val i = a.ordinal
            if (p.position != Position.GK && a.group == AttrGroup.GOALKEEPING) continue
            var d = if (netAvg > 0) {
                netAvg * (if (w[i] > 0) 1.0 else 0.45) * focusBoost(focus, a) * (0.5 + rng.nextDouble())
            } else {
                // Decline: physical attributes fall much faster; mental ones can still improve slightly.
                when (a.group) {
                    AttrGroup.PHYSICAL -> netAvg * 2.2
                    AttrGroup.MENTAL -> if (a in setOf(Attr.DECISIONS, Attr.COMPOSURE, Attr.ANTICIPATION, Attr.LEADERSHIP)) -netAvg * 0.15 else netAvg * 0.3
                    else -> netAvg * 0.6
                } * (0.5 + rng.nextDouble())
            }
            if (a in physical && age < 21 && netAvg > 0) d *= 1.3
            val nv = (p.attrs[i] + Math.round(d * 100).toInt()).coerceIn(100, 2000)
            p.attrs[i] = nv.toShort()
        }
        val before = p.ca
        Ability.recompute(p)
        // Potential is a hard ceiling.
        if (p.ca > p.pa && p.ca > before) {
            for (i in p.attrs.indices) if (w[i] > 0) p.attrs[i] = (p.attrs[i] - 8).coerceAtLeast(100).toShort()
            Ability.recompute(p)
        }
        // A failing career slowly erodes potential (no minutes in late teens/twenties).
        if (age > 21 && gamesShare < 0.15 && p.pa > p.ca + 5 && rng.chance(0.05)) p.pa--
        if (age > 27) p.pa = maxOf(p.ca, p.pa - (if (rng.chance(0.3)) 1 else 0))
    }

    private fun weeksIntoSeason(world: World): Int {
        val start = Cal.of(world.season, 8, 8)
        return ((world.today - start) / 7).coerceAtLeast(1)
    }

    /** Daily recovery of condition, injury countdown and morale drift. */
    fun daily(world: World) {
        for (p in world.players.values) {
            val nat = p.attr(Attr.NATURAL_FITNESS) / 20.0
            val club = world.clubs[p.playingFor]
            val physio = club?.staffIds?.mapNotNull { world.staff[it] }?.firstOrNull { it.role == StaffRole.PHYSIO }?.medical ?: 6
            if (p.injuryDays > 0) {
                p.injuryDays -= if (physio >= 15 && (world.today + p.id) % 4 == 0L) 2 else 1
                if (p.injuryDays <= 0) { p.injuryDays = 0; p.injuryName = ""; p.condition = 70 }
            } else {
                p.condition = (p.condition + (5 + 4 * nat).toInt()).coerceAtMost(100)
            }
            if (p.morale > 70) p.morale-- else if (p.morale < 60) p.morale++
        }
    }

    /** Training injuries (rare), checked weekly. */
    fun trainingInjuries(world: World) {
        for (club in world.clubs.values) {
            val rng = Rng.stream(world.seed, "trainInj", club.id, world.today.toLong())
            val risk = 0.0025 * (club.trainingIntensity / 2.0)
            for (id in club.playerIds) {
                val p = world.players[id] ?: continue
                if (p.injuryDays > 0) continue
                if (rng.chance(risk * (0.5 + p.injuryProneness / 20.0))) {
                    val inj = rng.pick(NamePools.injuries.filter { it.second < 30 })
                    p.injuryDays = (inj.second * rng.range(0.5, 1.4)).toInt().coerceAtLeast(2)
                    p.injuryName = inj.first
                    if (club.id == world.manager.clubId) {
                        world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.INJURY, "injury", listOf(p.name, p.injuryName, p.injuryDays.toString()), ref = p.id))
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ youth intake

    /** Annual youth intake (mid-March). Returns the generated players. */
    fun youthIntake(world: World): List<Player> {
        val all = mutableListOf<Player>()
        val counts = world.players.values.groupingBy { it.nationId }.eachCount()
        for (club in world.clubs.values.sortedBy { it.id }) {
            val nation = world.nation(club.nationId)
            val rng = Rng.stream(world.seed, "youth", world.season.toLong(), club.id)
            val target = world.populationTarget[nation.id] ?: 1
            val current = counts[nation.id] ?: 1
            // Population control: intake shrinks quickly when a nation has more players than its target.
            val ratio = target.toDouble() / current
            val popF = (ratio * ratio).coerceIn(0.25, 1.8)
            val n = ((1.2 + club.youthRecruitment / 8.0 + rng.nextDouble() * 2) * popF).toInt().coerceIn(1, 7)
            val youthCoach = club.staffIds.mapNotNull { world.staff[it] }.firstOrNull { it.role == StaffRole.YOUTH_COACH }?.youthWork ?: 6
            val created = mutableListOf<Player>()
            repeat(n) {
                val pos = rng.weighted(Position.entries) { if (it == Position.GK) 0.6 else if (it == Position.DC || it == Position.MC || it == Position.ST) 1.6 else 1.0 }
                val paMean = 35 + nation.youthRating * 2.6 + club.youthFacilities * 2.0 + club.reputation * 0.3 + youthCoach * 0.6
                var pa = rng.gaussian(paMean, 22.0)
                if (rng.chance(0.015)) pa += rng.range(25.0, 55.0) // rare wonderkid
                val paI = pa.toInt().coerceIn(30, 195)
                val age = rng.range(15, 17)
                val ca = (paI * rng.range(0.28, 0.45)).toInt().coerceIn(15, 90)
                val p = Factory.player(world, rng, nation, pos, ca, age, pa = paI)
                // Personality influenced by the head of youth development
                p.pa = maxOf(p.pa, p.ca)
                world.players[p.id] = p
                Factory.signContract(world, p, club, 3, fm.engine.market.Valuation.youthWage(nation), SquadStatus.PROSPECT, youth = true)
                Factory.refreshValue(world, p)
                created.add(p)
            }
            all.addAll(created)
            if (club.id == world.manager.clubId && created.isNotEmpty()) {
                val best = created.maxBy { it.pa }
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.YOUTH, "youth_intake",
                    listOf(created.size.toString(), best.name, best.position.label(world.settings.lang)), ref = best.id))
            }
        }
        // Foreign talent pools: nations without leagues still produce players (as free agents).
        for (nation in world.nations) {
            if (world.competitions.any { it.nationId == nation.id && it.type == CompType.LEAGUE }) continue
            val target = world.populationTarget[nation.id] ?: continue
            val current = counts[nation.id] ?: 0
            val deficit = target - current
            if (deficit <= 0) continue
            val rng = Rng.stream(world.seed, "foreignpool", world.season.toLong(), nation.id.toLong())
            repeat(deficit.coerceAtMost(20)) {
                val pos = rng.pick(Position.entries)
                val p = Factory.player(world, rng, nation, pos, rng.range(70, 130), rng.range(22, 28))
                Factory.refreshValue(world, p)
                world.players[p.id] = p
                all.add(p)
            }
        }
        return all
    }

    // ------------------------------------------------------------------ retirement

    fun retirementChance(p: Player, today: Int): Double {
        val age = p.age(today)
        val gkShift = if (p.position == Position.GK) 2 else 0
        val a = age - gkShift
        var chance = when {
            a < 31 -> 0.0
            a < 33 -> 0.04
            a < 35 -> 0.2
            a < 37 -> 0.45
            a < 39 -> 0.75
            else -> 0.97
        }
        if (p.clubId < 0 && age >= 29) chance += 0.35
        // Unattached fringe players drop out of professional football.
        if (p.clubId < 0 && p.ca < 70) chance += 0.3
        if (p.clubId < 0 && age >= 24) chance += 0.15
        if (p.ca < 50 && age >= 30) chance += 0.2
        if (p.injuryDays > 120) chance += 0.2
        return chance.coerceAtMost(1.0)
    }

    /** End-of-season retirements. Retired players are archived (IDs are never reused). */
    fun retirements(world: World): List<Player> {
        val retired = mutableListOf<Player>()
        for (p in world.players.values.toList()) {
            val rng = Rng.stream(world.seed, "retire", p.id, world.season.toLong())
            if (rng.chance(retirementChance(p, world.today))) retired.add(p)
        }
        for (p in retired) {
            val club = world.clubs[p.clubId]
            club?.playerIds?.remove(p.id)
            if (p.loanClubId >= 0) world.clubs[p.loanClubId]?.playerIds?.remove(p.id)
            world.players.remove(p.id)
            val apps = p.history.sumOf { it.apps } + p.season.apps
            val goals = p.history.sumOf { it.goals } + p.season.goals
            val peak = maxOf(p.ca, p.history.maxOfOrNull { it.ca } ?: 0)
            val notable = peak >= 100 || apps >= 150 || p.history.any { it.clubId == world.manager.clubId }
            if (notable) {
                world.archive.add(ArchivedPerson(p.id, p.name, p.nationId, p.birthDay, world.today, p.position, peak, apps, goals,
                    p.history.map { it.clubName }.distinct()))
            }
            if (club != null && club.id == world.manager.clubId) {
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "retired", listOf(p.name, p.age(world.today).toString()), ref = p.id))
            }
            // Some retired players move into coaching (same person id, new career).
            val rng = Rng.stream(world.seed, "tocoach", p.id)
            val leader = (p.attr(Attr.LEADERSHIP) + p.attr(Attr.DETERMINATION) + p.attr(Attr.DECISIONS)) / 3
            if (rng.chance(0.08 + leader / 100.0)) {
                val nation = world.nation(p.nationId)
                val role = if (rng.chance(0.3)) StaffRole.MANAGER else rng.pick(listOf(StaffRole.COACH, StaffRole.SCOUT, StaffRole.YOUTH_COACH, StaffRole.ASSISTANT))
                val level = 4.0 + leader * 0.4 + peak / 40.0
                val s0 = Factory.staff(world, rng, nation, role, level, p.age(world.today))
                val s = Staff(p.id, p.firstName, p.lastName, p.nationId, p.birthDay, role, s0.coaching, s0.tactical,
                    s0.judgingAbility, s0.judgingPotential, s0.motivation, s0.discipline, s0.medical, s0.youthWork,
                    (peak / 3).coerceIn(5, 70), s0.style, formerPlayer = true)
                world.staff[s.id] = s
            }
        }
        return retired
    }

    /** Staff ageing: old staff retire; unemployed pool is kept at a healthy size. */
    fun staffTurnover(world: World) {
        val rng = Rng.stream(world.seed, "staffturnover", world.season.toLong())
        for (s in world.staff.values.toList()) {
            val age = s.age(world.today)
            if (age >= 68 || (age >= 62 && rng.chance(0.3)) || (s.clubId < 0 && age >= 58 && rng.chance(0.25))) {
                world.clubs[s.clubId]?.let { c -> c.staffIds.remove(s.id); if (c.managerId == s.id) c.managerId = -1 }
                world.staff.remove(s.id)
            }
        }
        val unemployedManagers = world.staff.values.count { it.clubId < 0 && it.role == StaffRole.MANAGER }
        repeat((20 - unemployedManagers).coerceAtLeast(0)) {
            val nation = world.nations[rng.nextInt(5)]
            val s = Factory.staff(world, rng, nation, StaffRole.MANAGER, rng.range(5.0, 12.0), rng.range(35, 55))
            world.staff[s.id] = s
        }
        // Clubs that lost staff to retirement hire replacements.
        for (club in world.clubs.values) {
            val roles = club.staffIds.mapNotNull { world.staff[it]?.role }
            val needed = listOf(StaffRole.ASSISTANT, StaffRole.COACH, StaffRole.GK_COACH, StaffRole.FITNESS_COACH, StaffRole.SCOUT, StaffRole.PHYSIO, StaffRole.YOUTH_COACH)
            for (r in needed) if (r !in roles) {
                val nation = world.nation(club.nationId)
                val s = Factory.staff(world, rng, nation, r, 5.0 + club.reputation / 9.0, rng.range(30, 55))
                s.clubId = club.id
                s.contract = Contract(club.id, world.today, world.season + 2, fm.engine.world.WorldGenerator.staffWage(nation, r, s.quality()))
                world.staff[s.id] = s
                club.staffIds.add(s.id)
            }
        }
    }
}
