package fm.engine.market

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.finance.Finance
import fm.engine.world.Factory

/**
 * Transfer market: windows, AI club strategies, contract renewals, free agents, loans, and the
 * user's negotiations. AI clubs act on a rotating schedule (a few clubs per day) so behaviour is
 * varied and the daily cost stays small.
 */
object Transfers {
    const val MIN_SQUAD = 20
    const val MAX_SQUAD = 30

    fun windowOpen(world: World): Boolean {
        val m = Cal.month(world.today)
        return m == 7 || m == 8 || m == 1
    }

    private val groups = mapOf(
        Line.GK to 3, Line.DEF to 8, Line.MID to 8, Line.ATT to 5,
    )

    // ------------------------------------------------------------------ AI market

    /** Called daily while a window is open. */
    fun aiDay(world: World) {
        val clubs = world.clubs.values.filter { it.id != world.manager.clubId }.sortedBy { it.id }
        for (club in clubs) {
            // Each club acts on roughly one day in four.
            if ((club.id + world.today) % 4 != 0L) continue
            val rng = Rng.stream(world.seed, "aimarket", club.id, world.today.toLong())
            val squad = world.squad(club.id)
            if (squad.size < MIN_SQUAD) signFreeAgent(world, club, rng, squad)
            if (squad.size > MAX_SQUAD) shedPlayer(world, club, rng, squad)
            if (rng.chance(0.35)) tryBuy(world, club, rng, squad)
            if (rng.chance(0.15)) loanOutYouth(world, club, rng, squad)
            if (rng.chance(0.2)) sellSurplusQuality(world, club, rng, squad)
        }
        bidsForUserListedPlayers(world)
    }

    private fun groupNeed(squad: List<Player>): Line? {
        val counts = squad.groupingBy { it.position.line }.eachCount()
        return groups.entries.maxByOrNull { (line, target) -> target - (counts[line] ?: 0) }?.takeIf { (line, target) -> target - (counts[line] ?: 0) > 0 }?.key
    }

    /** Average CA of the best players of the club in a line. */
    private fun lineLevel(squad: List<Player>, line: Line): Double {
        val n = when (line) { Line.GK -> 1; Line.DEF -> 4; Line.MID -> 4; Line.ATT -> 2 }
        val l = squad.filter { it.position.line == line }.sortedByDescending { it.ca }.take(n)
        return if (l.isEmpty()) 0.0 else l.map { it.ca }.average()
    }

    private fun wageRoom(world: World, club: Club): Long = club.wageBudget - Finance.playerWageBill(world, club)

    private fun tryBuy(world: World, club: Club, rng: Rng, squad: List<Player>) {
        val manager = world.staff[club.managerId]
        val style = manager?.style
        val line = groupNeed(squad) ?: rng.pick(Line.entries.filter { it != Line.GK || rng.chance(0.2) })
        val level = lineLevel(squad, line)
        val nation = world.nation(club.nationId)
        val budget = club.transferBudget
        val room = wageRoom(world, club)
        val youthTrust = style?.youthTrust ?: 0.5
        // Candidates: players at other clubs of lower/similar reputation in the same nation, plus free agents.
        val candidates = world.players.values.filter { p ->
            p.position.line == line && p.playingFor != club.id && p.ca > level + 3 && p.loanClubId < 0 &&
                (p.clubId < 0 || (world.clubs[p.clubId]?.let { it.nationId == club.nationId && it.reputation <= club.reputation + 5 } ?: false) ||
                    (p.transferListed && p.clubId >= 0))
        }
        if (candidates.isEmpty()) return
        val scored = candidates.map { p ->
            val age = p.age(world.today)
            val fee = if (p.clubId < 0) 0L else p.value
            val wage = Valuation.wage(p.ca, nation, club.reputation)
            val ageScore = if (age <= 23) 1.0 + youthTrust * 0.4 else if (age >= 31) 0.7 else 1.0
            val affordable = fee <= budget && wage <= room + (squad.filter { it.position.line == line }.minByOrNull { it.ca }?.contract?.wage ?: 0)
            Triple(p, (p.ca - level) * ageScore * (0.8 + rng.nextDouble() * 0.4), affordable)
        }.filter { it.third }.sortedByDescending { it.second }
        val pick = scored.firstOrNull()?.first ?: return
        val years = if (pick.age(world.today) <= 26) rng.range(2, 4) else rng.range(1, 2)
        val wage = Money.scale(Valuation.wage(pick.ca, nation, club.reputation), rng.range(0.95, 1.15))
        if (pick.clubId < 0) {
            completeTransfer(world, pick, null, club, 0, wage, years)
        } else {
            val seller = world.club(pick.clubId)
            val offer = Money.scale(pick.value, rng.range(0.9, 1.2) * (0.9 + (style?.riskTolerance ?: 0.5) * 0.2))
            if (seller.id == world.manager.clubId) {
                if (!pick.transferListed) return
                if (offer >= Money.scale(pick.value, 0.9)) completeTransfer(world, pick, seller, club, offer, wage, years)
                return
            }
            if (aiAcceptsBid(world, seller, pick, offer) && playerAcceptsMove(world, pick, seller, club, wage)) {
                completeTransfer(world, pick, seller, club, offer, wage, years)
            }
        }
    }

    fun aiAcceptsBid(world: World, seller: Club, p: Player, fee: Long): Boolean {
        val squad = world.squad(seller.id)
        val sameLine = squad.count { it.position.line == p.position.line }
        if (sameLine <= (groups[p.position.line] ?: 3) - 2) return false
        val key = p.contract?.squadStatus == SquadStatus.KEY
        val ask = Money.scale(p.value, if (key) 1.35 else if (p.transferListed) 0.8 else 1.05)
        if (p.contract?.releaseClause?.let { it > 0 && fee >= it } == true) return true
        return fee >= ask
    }

    fun playerAcceptsMove(world: World, p: Player, from: Club?, to: Club, wage: Long): Boolean {
        val curWage = p.contract?.wage ?: 0
        val repOk = to.reputation >= (from?.reputation ?: 0) - 12 + (p.personality.ambition - 10)
        val sameNation = to.nationId == p.nationId || p.personality.adaptability >= 8
        return repOk && sameNation && wage >= curWage
    }

    fun completeTransfer(world: World, p: Player, from: Club?, to: Club, fee: Long, wage: Long, years: Int, loan: Boolean = false) {
        if (from != null) {
            from.playerIds.remove(p.id)
            if (fee > 0) {
                Finance.income(from, Finance.SALES, fee)
                Finance.expense(to, Finance.PURCHASES, fee)
                val sellOn = p.contract?.sellOnPercent ?: 0
                if (sellOn > 0) { /* sell-on handled as part of fee for the original club (simplified) */ }
            }
        }
        if (p.loanClubId >= 0) { world.clubs[p.loanClubId]?.playerIds?.remove(p.id); p.loanClubId = -1 }
        val agentFee = fee / 20 + wage
        Finance.expense(to, Finance.AGENT, agentFee)
        to.transferBudget = (to.transferBudget - fee - agentFee).coerceAtLeast(0)
        Factory.signContract(world, p, to, years, wage, statusFor(world, to, p))
        p.transferListed = false
        p.morale = (p.morale + 8).coerceAtMost(100)
        Factory.refreshValue(world, p)
        world.transfers.add(TransferRecord(world.today, p.id, p.name, from?.id ?: -1, to.id, fee, loan, from == null))
        val user = world.manager.clubId
        val lang = world.settings.lang
        if (to.id == user) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.TRANSFER, "transfer_done_in",
            listOf(p.name, from?.name ?: "-", Money.format(fee, lang)), ref = p.id))
        if (from?.id == user) world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.TRANSFER, "transfer_done_out",
            listOf(p.name, to.name, Money.format(fee, lang)), ref = p.id))
    }

    private fun statusFor(world: World, club: Club, p: Player): SquadStatus {
        val squad = world.squad(club.id).sortedByDescending { it.ca }
        val rank = squad.indexOfFirst { it.ca <= p.ca }.let { if (it < 0) squad.size else it }
        return when {
            p.age(world.today) <= 20 && rank > 11 -> SquadStatus.PROSPECT
            rank < 3 -> SquadStatus.KEY
            rank < 11 -> SquadStatus.FIRST_TEAM
            rank < 18 -> SquadStatus.ROTATION
            else -> SquadStatus.BACKUP
        }
    }

    /**
     * Pre-season safety net: every club ends up with at least [MIN_SQUAD] players (free agents
     * first, otherwise academy call-ups), so retirements and expiries can never break a club.
     */
    fun ensureMinimumSquads(world: World) {
        for (club in world.clubs.values.sortedBy { it.id }) {
            val rng = Rng.stream(world.seed, "minsquad", club.id, world.today.toLong())
            var guard = 0
            while (world.squad(club.id).size < MIN_SQUAD && guard++ < 30) signFreeAgent(world, club, rng, world.squad(club.id))
        }
    }

    private fun signFreeAgent(world: World, club: Club, rng: Rng, squad: List<Player>) {
        val line = groupNeed(squad) ?: Line.MID
        val nation = world.nation(club.nationId)
        val room = wageRoom(world, club) + nation.wageIndex * 3
        val fa = world.players.values.filter { it.clubId < 0 && it.position.line == line }
            .filter { Valuation.wage(it.ca, nation, club.reputation) <= room }
            .sortedByDescending { it.ca }.take(5)
        val pick = if (fa.isNotEmpty()) rng.pick(fa) else {
            // Nobody suitable: promote an academy graduate (generated with the club's youth profile).
            val p = Factory.player(world, rng, nation, rng.pick(Position.entries.filter { it.line == line }),
                (world.squad(club.id).map { it.ca }.average().takeIf { !it.isNaN() } ?: 60.0).toInt() - 15, rng.range(17, 19))
            world.players[p.id] = p
            p
        }
        completeTransfer(world, pick, null, club, 0, Valuation.wage(pick.ca, nation, club.reputation), rng.range(1, 3))
    }

    private fun shedPlayer(world: World, club: Club, rng: Rng, squad: List<Player>) {
        val worst = squad.filter { it.loanClubId < 0 && it.clubId == club.id && it.contract?.youth != true }
            .minByOrNull { it.ca - (if (it.age(world.today) <= 21) 15 else 0) } ?: return
        // Try to sell to a smaller club in the same nation; otherwise release.
        val buyer = world.clubs.values.filter { it.nationId == club.nationId && it.id != club.id && it.id != world.manager.clubId &&
            it.reputation < club.reputation && world.squad(it.id).size < MAX_SQUAD - 2 }
            .sortedByDescending { it.reputation }.firstOrNull { b -> lineLevel(world.squad(b.id), worst.position.line) < worst.ca }
        if (buyer != null && buyer.transferBudget >= worst.value / 2) {
            val nation = world.nation(buyer.nationId)
            completeTransfer(world, worst, club, buyer, worst.value / 2, Valuation.wage(worst.ca, nation, buyer.reputation), rng.range(1, 3))
        } else release(world, worst)
    }

    /**
     * Big clubs do not hoard: a good player outside the top 18 who wants minutes is sold to a
     * smaller club in the same nation that can use him (spreads talent, keeps leagues competitive).
     */
    private fun sellSurplusQuality(world: World, club: Club, rng: Rng, squad: List<Player>) {
        val ranked = squad.filter { it.clubId == club.id && it.loanClubId < 0 }.sortedByDescending { it.ca }
        val surplus = ranked.drop(18).firstOrNull { it.age(world.today) in 22..30 && it.ca >= (ranked.getOrNull(10)?.ca ?: 999) - 12 } ?: return
        val buyers = world.clubs.values.filter { it.nationId == club.nationId && it.id != club.id && it.id != world.manager.clubId &&
            it.reputation < club.reputation && lineLevel(world.squad(it.id), surplus.position.line) < surplus.ca && it.transferBudget >= surplus.value * 0.6 }
        val buyer = buyers.maxByOrNull { it.reputation } ?: return
        val nation = world.nation(buyer.nationId)
        val wage = Valuation.wage(surplus.ca, nation, buyer.reputation)
        if (playerAcceptsMove(world, surplus, club, buyer, wage) || surplus.benchWeeks > 6)
            completeTransfer(world, surplus, club, buyer, Money.scale(surplus.value, 0.8), maxOf(wage, surplus.contract?.wage ?: 0), rng.range(2, 3))
    }

    fun release(world: World, p: Player) {
        val club = world.clubs[p.clubId] ?: return
        // Pay off a part of the remaining contract.
        val years = ((p.contract?.endYear ?: world.season) - world.season).coerceAtLeast(0)
        Finance.expense(club, Finance.WAGES, (p.contract?.wage ?: 0) * (2 + years * 3))
        club.playerIds.remove(p.id)
        if (p.loanClubId >= 0) world.clubs[p.loanClubId]?.playerIds?.remove(p.id)
        p.loanClubId = -1
        p.clubId = -1; p.contract = null; p.transferListed = false
    }

    private fun loanOutYouth(world: World, club: Club, rng: Rng, squad: List<Player>) {
        val ranked = squad.sortedByDescending { it.ca }
        val candidate = ranked.drop(20).firstOrNull { it.age(world.today) in 18..21 && it.pa > it.ca + 15 && it.loanClubId < 0 && it.clubId == club.id } ?: return
        val host = world.clubs.values.filter { it.nationId == club.nationId && it.reputation < club.reputation && it.id != world.manager.clubId &&
            world.squad(it.id).size < MAX_SQUAD - 3 }.let { if (it.isEmpty()) null else rng.pick(it) } ?: return
        loan(world, candidate, club, host)
    }

    fun loan(world: World, p: Player, parent: Club, host: Club) {
        parent.playerIds.remove(p.id)
        host.playerIds.add(p.id)
        p.loanClubId = host.id
        p.loanUntilDay = Cal.of(world.season + 1, 6, 30)
        world.transfers.add(TransferRecord(world.today, p.id, p.name, parent.id, host.id, 0, loan = true))
    }

    fun endLoans(world: World) {
        for (p in world.players.values) {
            if (p.loanClubId >= 0 && world.today >= p.loanUntilDay) {
                world.clubs[p.loanClubId]?.playerIds?.remove(p.id)
                p.loanClubId = -1
                world.clubs[p.clubId]?.let { if (p.id !in it.playerIds) it.playerIds.add(p.id) }
            }
        }
    }

    private fun bidsForUserListedPlayers(world: World) {
        val user = world.userClub ?: return
        for (p in world.squad(user.id).filter { it.transferListed && it.clubId == user.id }) {
            if ((p.id + world.today) % 7 != 0L) continue
            val rng = Rng.stream(world.seed, "userbid", p.id, world.today.toLong())
            val buyers = world.clubs.values.filter { it.id != user.id && it.transferBudget >= p.value * 0.8 && it.reputation >= user.reputation - 25 }
            if (buyers.isEmpty()) continue
            val buyer = rng.pick(buyers)
            val fee = Money.scale(p.value, rng.range(0.8, 1.15))
            val nation = world.nation(buyer.nationId)
            val wage = Valuation.wage(p.ca, nation, buyer.reputation)
            if (fee >= Money.scale(p.value, 0.9) && playerAcceptsMove(world, p, user, buyer, wage)) {
                completeTransfer(world, p, user, buyer, fee, wage, rng.range(2, 4))
            } else {
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.TRANSFER, "offer_received",
                    listOf(buyer.name, p.name, Money.format(fee, world.settings.lang)), ref = p.id))
            }
        }
    }

    // ------------------------------------------------------------------ contracts

    /** AI renewals (January and June). Players the club wants are re-signed, others run down. */
    fun aiRenewals(world: World) {
        for (club in world.clubs.values) {
            if (club.id == world.manager.clubId) continue
            val squad = world.squad(club.id).filter { it.clubId == club.id }.sortedByDescending { it.ca }
            squad.forEachIndexed { rank, p ->
                val c = p.contract ?: return@forEachIndexed
                if (c.endYear > world.season + 1) return@forEachIndexed
                val rng = Rng.stream(world.seed, "renew", p.id, world.today.toLong())
                val age = p.age(world.today)
                val wanted = rank < 22 && age < 33 || (age <= 21 && p.pa > p.ca + 10)
                if (wanted && rng.chance(0.75)) {
                    val nation = world.nation(club.nationId)
                    val years = if (age < 28) rng.range(2, 4) else 1
                    p.contract = c.copy(endYear = world.season + 1 + years, wage = maxOf(c.wage, Valuation.wage(p.ca, nation, club.reputation)), youth = false)
                }
            }
        }
    }

    /** Season end: expired contracts make players free agents. */
    fun expireContracts(world: World) {
        for (p in world.players.values.toList()) {
            val c = p.contract ?: continue
            if (c.endYear <= world.season + 1) {
                val club = world.clubs[c.clubId]
                if (club?.id == world.manager.clubId) {
                    world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.CONTRACT, "player_left_free", listOf(p.name), ref = p.id))
                }
                club?.playerIds?.remove(p.id)
                if (p.loanClubId >= 0) world.clubs[p.loanClubId]?.playerIds?.remove(p.id)
                p.loanClubId = -1
                p.clubId = -1; p.contract = null; p.transferListed = false
            }
        }
    }

    /** Warn the user about contracts in their final season (once, in January). */
    fun warnUserContracts(world: World) {
        val user = world.userClub ?: return
        for (p in world.squad(user.id)) {
            val c = p.contract ?: continue
            if (c.clubId == user.id && c.endYear <= world.season + 1) {
                world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.CONTRACT, "contract_expiring", listOf(p.name), ref = p.id))
            }
        }
    }

    // ------------------------------------------------------------------ user actions

    sealed class OfferResult {
        data class Accepted(val fee: Long) : OfferResult()
        data class Counter(val fee: Long) : OfferResult()
        data class PlayerRefused(val reasonKey: String) : OfferResult()
        data class Rejected(val reasonKey: String) : OfferResult()
    }

    /** Wage a player asks to join [club]. */
    fun wageDemand(world: World, p: Player, club: Club): Long {
        val nation = world.nation(club.nationId)
        val base = Valuation.wage(p.ca, nation, club.reputation)
        val ambition = 1.0 + (p.personality.ambition - 10) * 0.015
        return maxOf(Money.scale(base, ambition), p.contract?.wage?.let { Money.scale(it, 1.1) } ?: 0)
    }

    /** User bids for a player. Completes immediately on acceptance (when the window is open). */
    fun userOffer(world: World, playerId: Long, fee: Long, wage: Long, years: Int): OfferResult {
        val user = world.userClub ?: return OfferResult.Rejected("no_club")
        val p = world.players[playerId] ?: return OfferResult.Rejected("unknown")
        if (p.clubId == user.id) return OfferResult.Rejected("own_player")
        if (!windowOpen(world) && p.clubId >= 0) return OfferResult.Rejected("window_closed")
        if (fee > user.transferBudget) return OfferResult.Rejected("over_budget")
        if (wage - (p.contract?.wage ?: 0) > wageRoom(world, user) + world.nation(user.nationId).wageIndex * 2 &&
            Finance.playerWageBill(world, user) + wage > user.wageBudget) return OfferResult.Rejected("over_wage_budget")
        val seller = world.clubs[p.clubId]
        if (seller != null && !aiAcceptsBid(world, seller, p, fee)) {
            val ask = Money.scale(p.value, if (p.contract?.squadStatus == SquadStatus.KEY) 1.35 else 1.05)
            return if (ask <= user.transferBudget && fee >= ask / 2) OfferResult.Counter(ask) else OfferResult.Rejected("bid_too_low")
        }
        if (wage < wageDemand(world, p, user)) return OfferResult.PlayerRefused("wage_too_low")
        if (!playerAcceptsMove(world, p, seller, user, wage)) return OfferResult.PlayerRefused("not_interested")
        completeTransfer(world, p, seller, user, fee, wage, years.coerceIn(1, 5))
        return OfferResult.Accepted(fee)
    }

    /** User offers a new contract to one of their own players. */
    fun userRenew(world: World, playerId: Long, wage: Long, years: Int): Boolean {
        val user = world.userClub ?: return false
        val p = world.players[playerId] ?: return false
        if (p.clubId != user.id) return false
        val demand = Money.scale(Valuation.wage(p.ca, world.nation(user.nationId), user.reputation), 1.0 + (p.personality.ambition - 10) * 0.01)
        if (wage < demand && wage < (p.contract?.wage ?: 0) * 105 / 100) return false
        val c = p.contract!!
        p.contract = c.copy(endYear = world.season + 1 + years.coerceIn(1, 5), wage = wage, youth = false)
        p.morale = (p.morale + 5).coerceAtMost(100)
        world.inbox.add(InboxMessage(world.newId(), world.today, MsgType.CONTRACT, "renewed", listOf(p.name, "${world.season + 1 + years}"), ref = p.id))
        return true
    }

    fun renewalDemand(world: World, p: Player): Long {
        val club = world.clubs[p.clubId] ?: return 0
        return maxOf(Money.scale(Valuation.wage(p.ca, world.nation(club.nationId), club.reputation), 1.0 + (p.personality.ambition - 10) * 0.01),
            (p.contract?.wage ?: 0) * 105 / 100)
    }

    fun searchPlayers(world: World, positions: Set<Position>? = null, maxAge: Int = 99, maxValue: Long = Long.MAX_VALUE,
                      nationId: Int = -1, freeOnly: Boolean = false, query: String = ""): List<Player> {
        val q = query.trim().lowercase()
        return world.players.values.filter { p ->
            (positions == null || p.position in positions) && p.age(world.today) <= maxAge && p.value <= maxValue &&
                (nationId < 0 || p.nationId == nationId) && (!freeOnly || p.clubId < 0) && p.clubId != world.manager.clubId &&
                (q.isEmpty() || p.name.lowercase().contains(q))
        }.sortedByDescending { it.ca }
    }

    fun ratingAt(p: Player, pos: Position) = Ability.effective(p, pos)
}
