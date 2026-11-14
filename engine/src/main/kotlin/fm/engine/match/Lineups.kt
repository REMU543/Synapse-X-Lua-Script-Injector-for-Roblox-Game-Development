package fm.engine.match

import fm.core.*
import fm.engine.ability.Ability

/** Picks a starting XI and bench for a formation (used by AI managers and auto-pick). */
object Lineups {
    private val slotPriority = listOf(Position.GK, Position.DC, Position.ST, Position.DM, Position.MC, Position.DR,
        Position.DL, Position.AMC, Position.AMR, Position.AML, Position.MR, Position.ML)

    fun pick(world: World, club: Club, tactic: Tactic = club.tactic, maxSubs: Int = 5, familiarity: Double = 0.8): TeamSheet {
        val squad = world.squad(club.id).filter { it.available }
        val used = HashSet<Long>()
        val slots = tactic.effectiveSlots()
        val assigned = arrayOfNulls<Player>(11)
        // 1. Honour a user-chosen lineup where those players are available.
        if (tactic.lineup.size == 11) {
            tactic.lineup.forEachIndexed { i, id ->
                val p = squad.firstOrNull { it.id == id }
                if (p != null && p.id !in used) { assigned[i] = p; used.add(p.id) }
            }
        }
        // 2. Fill remaining slots greedily, most important positions first.
        val order = slots.indices.sortedBy { slotPriority.indexOf(slots[it].position) }
        for (i in order) {
            if (assigned[i] != null) continue
            val pos = slots[i].position
            val best = squad.filter { it.id !in used }
                .maxByOrNull { Ability.effective(it, pos) * (if (pos == Position.GK && it.position != Position.GK) 0.2 else 1.0) }
            if (best != null) { assigned[i] = best; used.add(best.id) }
        }
        val starters = slots.indices.mapNotNull { i -> assigned[i]?.let { it to slots[i] } }
        // Bench: a goalkeeper first, then the best remaining players.
        val rest = squad.filter { it.id !in used }.sortedByDescending { it.ca }
        val bench = mutableListOf<Player>()
        if (tactic.bench.isNotEmpty()) tactic.bench.mapNotNull { id -> rest.firstOrNull { it.id == id } }.forEach { if (bench.size < 9) bench.add(it) }
        rest.firstOrNull { it.position == Position.GK && it !in bench }?.let { if (bench.size < 9) bench.add(it) }
        for (p in rest) if (bench.size < 9 && p !in bench) bench.add(p)
        return TeamSheet(club.id, club.name, club.primaryColor, club.secondaryColor, starters, bench,
            tactic.instructions, tactic.formation, maxSubs, familiarity,
            tactic.penaltyTakerId, tactic.freeKickTakerId, tactic.cornerTakerId, tactic.cornerRoutine, tactic.captainId)
    }
}
