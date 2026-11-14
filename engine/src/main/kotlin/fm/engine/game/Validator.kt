package fm.engine.game

import fm.core.*
import fm.engine.finance.Finance

/** World integrity checks, run after loading a save and every season in the stress test. */
object Validator {
    fun validate(world: World): List<String> {
        val issues = mutableListOf<String>()
        val ids = HashSet<Long>()
        for (p in world.players.values) if (!ids.add(p.id)) issues += "duplicate player id ${p.id}"
        for (s in world.staff.values) if (s.id in world.players) issues += "person ${s.id} is both active player and staff"
        if (world.nextId <= (ids.maxOrNull() ?: 0)) issues += "id sequence behind existing ids"
        for (club in world.clubs.values) {
            for (pid in club.playerIds) {
                val p = world.players[pid]
                if (p == null) issues += "club ${club.id} lists missing player $pid"
                else if (p.playingFor != club.id) issues += "club ${club.id} lists player $pid who plays for ${p.playingFor}"
            }
            if (club.playerIds.toSet().size != club.playerIds.size) issues += "club ${club.id} lists a player twice"
            if (!Finance.ledgerConsistent(club)) issues += "club ${club.id} ledger inconsistent"
            if (club.playerIds.size < 14) issues += "club ${club.id} squad too small (${club.playerIds.size})"
        }
        for (p in world.players.values) {
            if (p.playingFor >= 0) {
                val c = world.clubs[p.playingFor]
                if (c == null) issues += "player ${p.id} at missing club ${p.playingFor}"
                else if (p.id !in c.playerIds) issues += "player ${p.id} not listed by club ${c.id}"
            }
            if (p.clubId >= 0 && p.contract?.clubId != p.clubId) issues += "player ${p.id} contract mismatch"
            if (p.ca > p.pa + 1) issues += "player ${p.id} CA above PA"
            if (p.attrs.size != Attr.COUNT) issues += "player ${p.id} attribute count"
        }
        for (comp in world.competitions) {
            val cs = world.compSeasons[comp.id] ?: run { issues += "competition ${comp.id} has no season"; null } ?: continue
            if (comp.type == CompType.LEAGUE && cs.teams.size != comp.league!!.teams) issues += "league ${comp.id} has ${cs.teams.size} teams"
        }
        return issues
    }
}
