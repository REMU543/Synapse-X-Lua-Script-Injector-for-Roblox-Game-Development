package fm.engine.game

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.dynamics.Press
import fm.engine.dynamics.TeamTalk
import fm.engine.match.*

/** Pre-match analysis, assistant advice during the match and the post-match report. */
object Assistant {
    data class Preview(
        val opponent: Club,
        val home: Boolean,
        val competition: Competition,
        val opponentPosition: Int,
        val ownPosition: Int,
        val opponentForm: List<Char>,
        val opponentFormation: String,
        val opponentMentality: Mentality,
        val opponentKeyPlayers: List<Player>,
        val ownUnavailable: List<Player>,
        val tiredStarters: List<Player>,
        val winPct: Int, val drawPct: Int, val lossPct: Int,
        val favourite: Boolean,
        val adviceKeys: List<Pair<String, List<String>>>,
    )

    fun preview(career: Career, f: Fixture): Preview {
        val w = career.world
        val uid = w.manager.clubId
        val home = f.homeId == uid
        val opp = w.club(if (home) f.awayId else f.homeId)
        val own = w.club(uid)
        val setup = career.setupFor(f)
        val oppSheet = if (home) setup.away else setup.home
        val ownSheet = if (home) setup.home else setup.away
        val (lh, la) = QuickSim.expectedGoals(setup.home, setup.away, setup.neutral)
        val (gf, ga) = if (home) lh to la else la to lh
        // Outcome probabilities from independent Poisson goals.
        fun pois(l: Double, k: Int): Double { var r = Math.exp(-l); for (i in 1..k) r *= l / i; return r }
        var win = 0.0; var draw = 0.0
        for (a in 0..8) for (b in 0..8) { val pr = pois(gf, a) * pois(ga, b); if (a > b) win += pr else if (a == b) draw += pr }
        val loss = (1 - win - draw).coerceAtLeast(0.0)
        val advice = mutableListOf<Pair<String, List<String>>>()
        val ins = oppSheet.instructions
        if (ins.defensiveLine > 0.62) advice += "adv_pre_highline" to emptyList()
        if (ins.pressing > 0.65) advice += "adv_pre_press" to emptyList()
        if (ins.mentality.value <= -0.5) advice += "adv_pre_lowblock" to emptyList()
        oppSheet.starters.maxByOrNull { if (it.second.position.line == Line.ATT) it.first.ca else 0 }?.let { advice += "adv_pre_danger" to listOf(it.first.name) }
        if (win < 0.25) advice += "adv_pre_underdog" to emptyList()
        if (win > 0.6) advice += "adv_pre_favourite" to emptyList()
        val tired = ownSheet.starters.map { it.first }.filter { it.condition < 80 }
        if (tired.isNotEmpty()) advice += "adv_pre_tired" to listOf(tired.joinToString { it.shortName })
        return Preview(
            opp, home, w.comp(f.compId),
            fm.engine.competition.CompetitionEngine.position(w, opp.id), fm.engine.competition.CompetitionEngine.position(w, uid),
            opp.form.takeLast(5), oppSheet.formation.name, ins.mentality,
            oppSheet.starters.map { it.first }.sortedByDescending { it.ca }.take(3),
            w.squad(uid).filter { !it.available },
            tired,
            Math.round(win * 100).toInt(), Math.round(draw * 100).toInt(), Math.round(loss * 100).toInt(),
            win > loss + 0.1,
            advice,
        )
    }

    /** Live advice for the user's team based on the match so far. */
    fun live(engine: MatchEngine2D): List<Pair<String, List<String>>> {
        val t = engine.setup.userTeam
        if (t < 0) return emptyList()
        val s = engine.stats; val o = 1 - t
        val out = mutableListOf<Pair<String, List<String>>>()
        val minute = engine.minute
        val poss = s[t].possessionTicks.toDouble() / (s[0].possessionTicks + s[1].possessionTicks).coerceAtLeast(1)
        if (minute >= 15 && poss < 0.4) out += "adv_live_possession" to listOf("${(poss * 100).toInt()}")
        if (minute >= 20 && s[o].shots >= s[t].shots + 5) out += "adv_live_shots_against" to listOf("${s[o].shots}", "${s[t].shots}")
        if (minute >= 30 && s[t].shots > 6 && s[t].xg / s[t].shots < 0.07) out += "adv_live_poor_chances" to emptyList()
        val diff = engine.goals[t] - engine.goals[o]
        if (minute >= 65 && diff < 0) out += "adv_live_chase" to emptyList()
        if (minute >= 75 && diff > 0) out += "adv_live_protect" to emptyList()
        for (i in t * 11 until t * 11 + 11) {
            val p = engine.playerAt[i] ?: continue
            if (!engine.active[i]) continue
            if (engine.energy[i] < 0.6) out += "adv_live_tired" to listOf(p.shortName)
            val m = engine.pm[p.id] ?: continue
            if (m.yellow == 1 && minute >= 55 && engine.slotAt[i]?.position?.line == Line.DEF) out += "adv_live_booked" to listOf(p.shortName)
            if (minute >= 55 && m.rating < 5.9) out += "adv_live_poor_player" to listOf(p.shortName)
        }
        return out.take(5)
    }

    data class PlayerLine(val player: Player, val position: Position?, val minutes: Int, val rating: Double, val goals: Int, val assists: Int, val yellow: Int, val red: Boolean)
    data class Report(val result: MatchResult, val homeName: String, val awayName: String, val lines: List<List<PlayerLine>>, val motmId: Long)

    fun report(world: World, engine: MatchEngine2D, result: MatchResult): Report {
        val lines = (0..1).map { t ->
            result.players.values.filter { it.team == t }.mapNotNull { m ->
                val p = world.players[m.playerId] ?: return@mapNotNull null
                val pos = (0 until 22).firstOrNull { engine.playerAt[it]?.id == p.id }?.let { engine.slotAt[it]?.position }
                PlayerLine(p, pos, m.minutes, m.rating, m.goals, m.assists, m.yellow, m.red)
            }.sortedBy { it.position?.ordinal ?: 99 }
        }
        val motm = result.players.values.maxByOrNull { it.rating }?.playerId ?: -1
        return Report(result, engine.sheets[0].clubName, engine.sheets[1].clubName, lines, motm)
    }
}

/** Match-day state machine for the user's match, used by the UI. */
class MatchDay(val career: Career, val fixture: Fixture) {
    val world get() = career.world
    val preview = Assistant.preview(career, fixture)
    var engine: MatchEngine2D? = null; private set
    var report: Assistant.Report? = null; private set
    var preTalk: List<TeamTalk.Reaction> = emptyList(); private set
    var halfTalk: List<TeamTalk.Reaction> = emptyList(); private set
    var fullTalk: List<TeamTalk.Reaction> = emptyList(); private set
    val userTeam: Int get() = if (fixture.homeId == world.manager.clubId) 0 else 1

    fun start(): MatchEngine2D {
        val e = career.prepareUserMatch()!!
        engine = e
        if (preTalk.isNotEmpty()) e.applyMotivation(userTeam, preTalk.associate { it.playerId to it.value })
        return e
    }

    private fun squadPlayers(): List<Player> {
        val e = engine
        if (e == null) {
            val s = career.setupFor(fixture)
            val sheet = if (userTeam == 0) s.home else s.away
            return sheet.starters.map { it.first } + sheet.bench
        }
        val sheet = e.sheets[userTeam]
        return ((userTeam * 11 until userTeam * 11 + 11).mapNotNull { e.playerAt[it] } + sheet.bench).distinctBy { it.id }
    }

    fun scoreDiff(): Int = engine?.let { it.goals[userTeam] - it.goals[1 - userTeam] } ?: 0

    fun talk(stage: TeamTalk.Stage, tone: TeamTalk.Tone): List<TeamTalk.Reaction> {
        val r = TeamTalk.react(world, squadPlayers(), tone, stage, scoreDiff(), preview.favourite, stage.ordinal.toLong())
        val mot = TeamTalk.applyMorale(world, r, stage)
        when (stage) {
            TeamTalk.Stage.PRE -> { preTalk = r; engine?.applyMotivation(userTeam, mot) }
            TeamTalk.Stage.HALF -> { halfTalk = r; engine?.applyMotivation(userTeam, mot) }
            TeamTalk.Stage.FULL -> fullTalk = r
        }
        return r
    }

    fun suggestedTone(stage: TeamTalk.Stage) = TeamTalk.suggest(stage, scoreDiff(), preview.favourite)

    /** Finish the match (simulating any remaining time), record it and prepare the report & press. */
    fun finish(): Assistant.Report {
        val e = engine ?: start()
        while (e.step()) { /* run out the clock */ }
        val result = e.result()
        val rep = Assistant.report(world, e, result)
        career.completeUserMatch(e)
        report = rep
        world.pendingPress = Press.postMatch(world, fixture, rep.motmId)
        return rep
    }

    /** Pre-match press conference (created once on match day). */
    fun pressPre(): PressConference {
        val existing = world.pendingPress
        if (existing != null && existing.pre && existing.fixtureId == fixture.id) return existing
        val p = Press.preMatch(world, fixture)
        world.pendingPress = p
        return p
    }
}
