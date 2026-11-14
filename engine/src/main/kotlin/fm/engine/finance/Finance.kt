package fm.engine.finance

import fm.core.*
import kotlin.math.pow

/**
 * Club finances. Every money movement goes through [post], which updates the balance and the
 * per-category season totals, so `balance == openingBalance + Σincome − Σexpense` always holds
 * (checked by the validator). All amounts are exact Long Rupiah.
 */
object Finance {
    const val TV = "tv"; const val MATCHDAY = "matchday"; const val COMMERCIAL = "commercial"; const val PRIZE = "prize"
    const val SALES = "transfer_sales"; const val OWNER = "owner"
    const val WAGES = "wages"; const val STAFF = "staff_wages"; const val UPKEEP = "upkeep"; const val PURCHASES = "transfer_purchases"
    const val BONUS = "signing_bonus"; const val AGENT = "agent_fees"

    fun income(club: Club, category: String, amount: Long) {
        if (amount <= 0) return
        club.balance += amount
        club.finance.income.merge(category, amount, Long::plus)
    }

    fun expense(club: Club, category: String, amount: Long) {
        if (amount <= 0) return
        club.balance -= amount
        club.finance.expense.merge(category, amount, Long::plus)
    }

    fun playerWageBill(world: World, club: Club): Long =
        world.players.values.filter { it.contract?.clubId == club.id }.sumOf { it.contract!!.wage }

    fun staffWageBill(world: World, club: Club): Long =
        club.staffIds.mapNotNull { world.staff[it] }.sumOf { it.contract?.wage ?: 0 }

    fun commercialIncome(club: Club): Long {
        val ratio = (club.reputation.toDouble() / club.commercialRepRef.coerceAtLeast(1)).coerceIn(0.2, 4.0)
        return Math.round(club.commercialBase * ratio.pow(1.4))
    }

    fun attendance(world: World, home: Club, away: Club, rng: Rng, importance: Double = 1.0): Int {
        val form = home.form.takeLast(5).count { it == 'W' } / 5.0
        val demand = home.fanBase * (0.55 + 0.35 * form + 0.1 * rng.nextDouble()) *
            (0.9 + away.reputation / 250.0) * importance
        return demand.toInt().coerceIn(500, home.stadiumCapacity)
    }

    fun matchdayIncome(world: World, home: Club, attendance: Int): Long =
        attendance.toLong() * world.nation(home.nationId).ticketPrice

    fun upkeep(world: World, club: Club): Long {
        val n = world.nation(club.nationId)
        return n.wageIndex * (club.trainingFacilities + club.youthFacilities + club.stadiumCapacity / 4000) / 4
    }

    /** Runs on the first day of every month. */
    fun monthly(world: World, club: Club) {
        expense(club, WAGES, playerWageBill(world, club))
        expense(club, STAFF, staffWageBill(world, club))
        expense(club, UPKEEP, upkeep(world, club))
        val league = world.comp(club.leagueId).league
        income(club, TV, (league?.tvMoneyPerClub ?: 0) / 12)
        income(club, COMMERCIAL, commercialIncome(club) / 12)
        club.finance.monthly.add(longArrayOf(world.today.toLong(), club.balance))
        while (club.finance.monthly.size > 24) club.finance.monthly.removeAt(0)
        rescueIfInsolvent(world, club)
    }

    /** Owners cover extreme debt so the world stays playable for decades (logged as income). */
    private fun rescueIfInsolvent(world: World, club: Club) {
        val annual = estimatedAnnualRevenue(world, club)
        if (club.balance < -annual / 2) {
            income(club, OWNER, -club.balance + annual / 10)
            club.debtRescues++
            if (club.id == world.manager.clubId) club.boardConfidence = (club.boardConfidence - 15).coerceAtLeast(0)
        }
    }

    fun estimatedAnnualRevenue(world: World, club: Club): Long {
        val league = world.comp(club.leagueId).league
        val homeGames = ((league?.teams ?: 18) - 1) * (league?.rounds ?: 2) / 2
        val att = (club.fanBase * 0.7).toInt().coerceAtMost(club.stadiumCapacity)
        return (league?.tvMoneyPerClub ?: 0) + commercialIncome(club) +
            homeGames * att.toLong() * world.nation(club.nationId).ticketPrice
    }

    /** Sets transfer and wage budgets from revenue and balance (sustainability rule). */
    fun setBudgets(world: World, club: Club) {
        val revenue = estimatedAnnualRevenue(world, club)
        // Wage cap: at most ~85% of revenue goes to player wages.
        club.wageBudget = (revenue * 0.85 / 12).toLong().coerceAtLeast(playerWageBill(world, club) / 2)
        // Transfer budget: a share of positive balance, never more than half a year's revenue.
        club.transferBudget = (club.balance * 0.35).toLong().coerceIn(0, revenue / 2)
    }

    /** Closes the season's books and opens a new ledger period. */
    fun closeSeason(world: World, club: Club) {
        val inc = club.finance.income.values.sum()
        val exp = club.finance.expense.values.sum()
        club.finance.seasons.add(longArrayOf(world.season.toLong(), inc, exp, club.balance))
        while (club.finance.seasons.size > 60) club.finance.seasons.removeAt(0)
        club.finance.income.clear()
        club.finance.expense.clear()
        club.finance.openingBalance = club.balance
    }

    fun ledgerConsistent(club: Club): Boolean =
        club.balance == club.finance.openingBalance + club.finance.income.values.sum() - club.finance.expense.values.sum()
}
