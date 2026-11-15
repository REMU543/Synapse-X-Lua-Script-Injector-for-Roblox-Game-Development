package fm.engine.dynamics

import fm.core.*

/**
 * Squad dynamics: influence hierarchy, player happiness (with explained factors), cohesion and
 * dressing-room atmosphere, promises and player requests. Updated weekly for the user's club.
 */
object Dynamics {
    enum class Tier(val id: String, val en: String) {
        LEADER("Pemimpin Tim", "Team Leader"),
        HIGHLY_INFLUENTIAL("Sangat Berpengaruh", "Highly Influential"),
        INFLUENTIAL("Berpengaruh", "Influential"),
        OTHER("Pemain Lain", "Other Players");
        fun label(lang: Lang) = if (lang == Lang.ID) id else en
    }

    data class HierarchyEntry(val player: Player, val influence: Double, val tier: Tier)

    /** Influence from leadership, reputation, age, tenure and status. */
    fun influence(world: World, p: Player): Double {
        val tenureYears = ((world.today - p.joinedDay) / 365.0).coerceIn(0.0, 10.0)
        val age = p.age(world.today)
        val status = when (p.contract?.squadStatus) { SquadStatus.KEY -> 12.0; SquadStatus.FIRST_TEAM -> 7.0; SquadStatus.ROTATION -> 3.0; else -> 0.0 }
        return p.attr(Attr.LEADERSHIP) * 2.2 + p.reputation * 0.35 + (age - 18).coerceIn(0, 14) * 0.9 + tenureYears * 2.0 + status +
            p.attr(Attr.DETERMINATION) * 0.5
    }

    fun hierarchy(world: World, clubId: Long): List<HierarchyEntry> {
        val ranked = world.squad(clubId).map { it to influence(world, it) }.sortedByDescending { it.second }
        return ranked.mapIndexed { i, (p, inf) ->
            val tier = when {
                i < 2 -> Tier.LEADER
                i < 6 -> Tier.HIGHLY_INFLUENTIAL
                i < 11 -> Tier.INFLUENTIAL
                else -> Tier.OTHER
            }
            HierarchyEntry(p, inf, tier)
        }
    }

    /** A happiness factor shown to the user: positive or negative, with weight. */
    data class Factor(val keyId: String, val keyEn: String, val value: Int) {
        fun label(lang: Lang) = if (lang == Lang.ID) keyId else keyEn
    }

    fun expectedShare(status: SquadStatus?): Double = when (status) {
        SquadStatus.KEY -> 0.85; SquadStatus.FIRST_TEAM -> 0.65; SquadStatus.ROTATION -> 0.35
        SquadStatus.BACKUP -> 0.12; SquadStatus.PROSPECT -> 0.1; null -> 0.0
    }

    fun playingShare(world: World, p: Player): Double {
        val club = world.clubs[p.playingFor] ?: return 0.0
        val clubMatches = world.compSeasons.values.flatMap { it.fixtures }.count { it.played && (it.homeId == club.id || it.awayId == club.id) }
        return if (clubMatches == 0) -1.0 else p.season.apps.toDouble() / clubMatches
    }

    fun factors(world: World, p: Player): List<Factor> {
        val out = mutableListOf<Factor>()
        val share = playingShare(world, p).let { if (p.reserve && p.age(world.today) <= 21 && it >= 0) maxOf(it, 0.5) else it }
        val exp = expectedShare(p.contract?.squadStatus)
        if (share >= 0) {
            val gap = share - exp
            when {
                gap < -0.3 -> out += Factor("Kurang waktu bermain", "Lack of playing time", -25)
                gap < -0.12 -> out += Factor("Ingin bermain lebih sering", "Wants more playing time", -10)
                gap > 0.15 -> out += Factor("Senang dengan waktu bermain", "Happy with playing time", 8)
            }
        }
        if (p.reserve && p.age(world.today) >= 23 && p.contract?.squadStatus in setOf(SquadStatus.KEY, SquadStatus.FIRST_TEAM, SquadStatus.ROTATION))
            out += Factor("Ingin kembali ke tim utama", "Wants first-team football", -15)
        if (p.reserve && p.age(world.today) <= 21 && p.reserveApps >= 5) out += Factor("Berkembang di tim cadangan", "Developing in the reserves", 4)
        val c = p.contract
        if (c != null && c.endYear <= world.season + 1 && p.age(world.today) < 32) out += Factor("Kontrak hampir habis", "Contract running out", -8)
        if (c != null) {
            val fair = fm.engine.market.Valuation.wage(p.ca, world.nation(world.clubs[c.clubId]?.nationId ?: p.nationId), world.clubs[c.clubId]?.reputation ?: 50)
            if (c.wage < fair * 0.7) out += Factor("Merasa gajinya terlalu kecil", "Feels underpaid", -10)
            else if (c.wage > fair * 1.2) out += Factor("Puas dengan gajinya", "Well paid", 5)
        }
        when {
            p.relationship >= 75 -> out += Factor("Hubungan baik dengan manajer", "Good relationship with manager", 10)
            p.relationship <= 30 -> out += Factor("Hubungan buruk dengan manajer", "Poor relationship with manager", -15)
        }
        val club = world.clubs[p.playingFor]
        if (club != null) {
            val form = club.form.takeLast(5)
            if (form.count { it == 'W' } >= 4) out += Factor("Tim sedang bagus", "Team in good form", 6)
            if (form.count { it == 'L' } >= 3) out += Factor("Tim sedang terpuruk", "Team in poor form", -6)
            if (p.personality.ambition >= 15 && club.reputation < p.reputation - 15) out += Factor("Merasa terlalu bagus untuk klub ini", "Feels too good for the club", -12)
        }
        if (p.season.apps >= 5 && p.season.avgRating >= 7.2) out += Factor("Bermain sangat baik", "Playing very well", 6)
        val broken = world.promises.count { it.playerId == p.id && it.resolved && !it.kept }
        if (broken > 0) out += Factor("Janji yang dilanggar", "Broken promise", -20 * broken)
        val kept = world.promises.count { it.playerId == p.id && it.resolved && it.kept }
        if (kept > 0) out += Factor("Manajer menepati janji", "Manager kept a promise", 8)
        if (p.injuryDays > 30) out += Factor("Frustrasi karena cedera panjang", "Frustrated by long injury", -6)
        return out
    }

    fun happiness(world: World, p: Player): Int = (62 + factors(world, p).sumOf { it.value } + (p.morale - 60) / 4).coerceIn(0, 100)

    fun happinessLabel(h: Int, lang: Lang): String = when {
        h >= 80 -> if (lang == Lang.ID) "Sangat senang" else "Very happy"
        h >= 62 -> if (lang == Lang.ID) "Senang" else "Content"
        h >= 45 -> if (lang == Lang.ID) "Biasa" else "Okay"
        h >= 30 -> if (lang == Lang.ID) "Tidak senang" else "Unhappy"
        else -> if (lang == Lang.ID) "Sangat kecewa" else "Very unhappy"
    }

    /** 0–100: how settled the squad is (shared tenure, shared nationality, low turnover). */
    fun cohesion(world: World, clubId: Long): Int {
        val squad = world.squad(clubId)
        if (squad.isEmpty()) return 0
        val tenure = squad.map { ((world.today - it.joinedDay) / 365.0).coerceIn(0.0, 4.0) }.average() / 4.0
        val mainNation = squad.groupingBy { it.nationId }.eachCount().maxOf { it.value }.toDouble() / squad.size
        val newSignings = world.transfers.count { it.toClub == clubId && world.today - it.day < 120 }
        return ((tenure * 55 + mainNation * 35 + 15 - newSignings * 3) + (world.userTacticFamiliarity - 50) * 0.1).toInt().coerceIn(0, 100)
    }

    /** Dressing-room atmosphere: happiness weighted by influence. */
    fun atmosphere(world: World, clubId: Long): Int {
        val h = hierarchy(world, clubId)
        if (h.isEmpty()) return 50
        var wsum = 0.0; var s = 0.0
        for (e in h) { val w = e.influence; wsum += w; s += w * e.player.happiness }
        return (s / wsum).toInt().coerceIn(0, 100)
    }

    /** Social groups: core (leaders & long-serving), nationality groups, the young players. */
    fun socialGroups(world: World, clubId: Long, lang: Lang): List<Pair<String, List<Player>>> {
        val squad = world.squad(clubId)
        val club = world.clubs[clubId] ?: return emptyList()
        val core = hierarchy(world, clubId).take(6).map { it.player }
        val foreign = squad.filter { it.nationId != club.nationId }.groupBy { it.nationId }
            .filter { it.value.size >= 2 }.map { (n, l) -> (if (lang == Lang.ID) "Kelompok " else "") + world.nation(n).name(lang) + (if (lang == Lang.EN) " group" else "") to l }
        val young = squad.filter { it.age(world.today) <= 21 && it !in core }
        val groups = mutableListOf((if (lang == Lang.ID) "Kelompok inti" else "Core group") to core)
        groups += foreign
        if (young.size >= 2) groups += (if (lang == Lang.ID) "Pemain muda" else "Young players") to young
        return groups
    }

    /** Weekly update for the user's club: happiness, morale drift, requests and promise checks. */
    fun weekly(world: World) {
        val club = world.userClub ?: return
        for (p in world.squad(club.id)) {
            p.happiness = happiness(world, p)
            if (p.happiness < 40) p.unhappyWeeks++ else p.unhappyWeeks = (p.unhappyWeeks - 1).coerceAtLeast(0)
            if (p.happiness < 45) p.morale = (p.morale - 2).coerceAtLeast(5)
            if (p.happiness > 75) p.morale = (p.morale + 1).coerceAtMost(100)
            if (p.unhappyWeeks == 4) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "dyn_unhappy", listOf(p.name), ref = p.id))
            if (p.unhappyWeeks >= 8 && !p.wantsOut && p.clubId == club.id) {
                p.wantsOut = true
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.TRANSFER, "dyn_transfer_request", listOf(p.name), ref = p.id))
            }
        }
        checkPromises(world)
        world.atmosphere = atmosphere(world, club.id)
    }

    fun checkPromises(world: World) {
        for (pr in world.promises.filter { !it.resolved }) {
            val p = world.players[pr.playerId]
            if (p == null || p.clubId != world.manager.clubId) { pr.resolved = true; pr.kept = true; continue }
            val kept = when (pr.type) {
                PromiseType.PLAYING_TIME -> p.season.apps - pr.appsAtStart >= 4 || (p.season.apps < pr.appsAtStart && p.history.lastOrNull()?.apps ?: 0 >= pr.appsAtStart + 4)
                PromiseType.NEW_CONTRACT -> (p.contract?.endYear ?: 0) > world.season + 2
                PromiseType.SIGN_PLAYER -> world.transfers.any { it.toClub == world.manager.clubId && it.day >= pr.madeDay &&
                    world.players[it.playerId]?.position?.line == p.position.line }
                PromiseType.SELL_IF_OFFER -> p.transferListed
            }
            if (kept) {
                pr.resolved = true; pr.kept = true
                p.relationship = (p.relationship + 10).coerceAtMost(100)
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "dyn_promise_kept", listOf(p.name), ref = p.id))
            } else if (world.today > pr.deadlineDay) {
                pr.resolved = true; pr.kept = false
                p.relationship = (p.relationship - 25).coerceAtLeast(0)
                p.morale = (p.morale - 15).coerceAtLeast(5)
                // Broken promises ripple through the dressing room via influential friends.
                for (e in hierarchy(world, world.manager.clubId).take(6)) if (e.player.id != p.id) e.player.morale = (e.player.morale - 3).coerceAtLeast(5)
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.NEWS, "dyn_promise_broken", listOf(p.name), ref = p.id))
            }
        }
        while (world.promises.size > 60) world.promises.removeAt(0)
    }
}
