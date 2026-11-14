package fm.engine.match

import fm.core.*
import kotlin.math.*

/**
 * Top-down 2D football simulation with 22 agents and a 2.5D ball.
 *
 * - Fixed timestep ([DT] = 0.1 s of match time). The simulation is pure and deterministic:
 *   same setup + same seed + same commands = same match.
 * - Simulation is separate from rendering: a renderer reads [px], [py], [ballX] ... and
 *   interpolates; it never mutates state. User commands go through [substitute] and
 *   [setInstructions].
 * - Every player decides from his attributes, slot role, team instructions, fatigue and the
 *   current situation (see [decide] and [computeTarget]).
 *
 * Pitch: x ∈ [0, 105] (length), y ∈ [0, 68] (width), goals centred on y = 34.
 */
class MatchEngine2D(val setup: MatchSetup) {
    companion object {
        const val DT = 0.1
        const val L = 105.0
        const val W = 68.0
        const val GOAL_HALF = 3.66
        const val GOAL_H = 2.44
        private const val N = 22
        /** Calibration knobs (see MatchCalibrationTest). */
        var SHOOT_WEIGHT = 1.0
        var FINISH_FACTOR = 1.0
        var XG_SCALE = 0.68
        var REACTION_BASE = 0.35
        var INTERCEPT_FACTOR = 0.6
        var PASS_DIST_PENALTY = 0.004
        var LANE_FACTOR = 0.8
    }

    enum class Phase { DEAD, LIVE, FINISHED }
    enum class Restart { KICKOFF, THROW_IN, GOAL_KICK, CORNER, FREE_KICK, PENALTY }

    private val rng = Rng(setup.seed)
    val sheets = arrayOf(setup.home, setup.away)

    // ------------------------------------------------------------------ players
    /** Player currently occupying each pitch index (substitutes take over an index). */
    val playerAt = arrayOfNulls<Player>(N)
    val slotAt = arrayOfNulls<Slot>(N)
    val active = BooleanArray(N)
    val px = DoubleArray(N); val py = DoubleArray(N)
    private val vx = DoubleArray(N); private val vy = DoubleArray(N)
    private val tx = DoubleArray(N); private val ty = DoubleArray(N)
    private val sprint = BooleanArray(N)
    val energy = DoubleArray(N)
    private val tackleCooldown = DoubleArray(N)
    private val runTimer = DoubleArray(N)
    /** Each opponent gets one interception attempt per ball flight. */
    private val triedIntercept = BooleanArray(N)

    // Cached normalised attributes [index][attr] in 0..1 (familiarity applied).
    private val a = Array(N) { DoubleArray(Attr.COUNT) }

    /** Team-talk motivation per pitch index (−1…+1); scales effective attributes by up to ±5%. */
    private val motivation = DoubleArray(N)
    private val motivationById = HashMap<Long, Double>()


    fun teamOf(i: Int) = if (i < 11) 0 else 1

    // ------------------------------------------------------------------ ball
    var ballX = L / 2; var ballY = W / 2; var ballZ = 0.0
    private var bvx = 0.0; private var bvy = 0.0; private var bvz = 0.0
    var owner = -1; private set
    private var lastTouch = -1
    private var lastTouchTeam = 0
    private var noTouchIdx = -1
    private var noTouchTime = 0.0
    private var passTarget = -1
    private var passer = -1
    private var passOffside = false
    private var passIsCross = false
    private var lastPasser = -1
    private var lastPassTime = -100.0
    private var decisionTimer = 0.0
    private var possessionTeam = 0
    private var possessionChangeTime = 0.0

    // Pending shot (outcome decided at the moment of striking, then animated).
    private enum class ShotOutcome { GOAL, SAVE_HOLD, SAVE_CORNER, SAVE_LOOSE, WIDE, OVER }
    private var shotPending: ShotOutcome? = null
    private var shooter = -1
    private var shotIsPenalty = false
    private var shotTick = 0L

    // ------------------------------------------------------------------ clock & state
    var phase = Phase.DEAD; private set
    var clock = 0.0; private set          // match seconds in the current period window
    var period = 1; private set           // 1,2 normal; 3,4 extra time
    private var periodEnd = 45 * 60.0
    private var restart = Restart.KICKOFF
    private var restartTeam = 0
    private var restartX = L / 2; private var restartY = W / 2
    private var deadTimer = 2.0
    private val dir = intArrayOf(1, -1)
    var tick = 0L; private set

    val goals = intArrayOf(0, 0)
    var penHome = -1; private set
    var penAway = -1; private set
    val stats = arrayOf(TeamStats(), TeamStats())
    val events = mutableListOf<MatchEvent>()
    val pm = LinkedHashMap<Long, PlayerMatch>()
    /** Diagnostic counters used by calibration tests. */
    val debug = LinkedHashMap<String, Double>()
    var debugLog: MutableList<String>? = null
    private fun dbg(k: String, v: Double = 1.0) { debug[k] = (debug[k] ?: 0.0) + v }
    private val subsUsed = intArrayOf(0, 0)
    private val pendingSubs = mutableListOf<Triple<Int, Int, Long>>() // team, index, incoming id
    private val minuteOn = HashMap<Long, Double>()
    var extraTimePlayed = false; private set

    val minute: Int get() = minuteNow() + 1

    /** Display clock including stoppage, e.g. "45+2". */
    val clockLabel: String get() {
        val base = when (period) { 1 -> 0; 2 -> 45; 3 -> 90; else -> 105 }
        val len = if (period <= 2) 45 else 15
        val m = (clock / 60).toInt()
        return if (m >= len) "${base + len}+${m - len + 1}" else "${base + m + 1}'"
    }

    init {
        for (t in 0..1) {
            val s = sheets[t]
            s.starters.forEachIndexed { k, (p, slot) ->
                if (k < 11) {
                    val i = t * 11 + k
                    place(i, p, slot)
                    pm[p.id] = PlayerMatch(p.id, t).also { it.started = true }
                    minuteOn[p.id] = 0.0
                }
            }
        }
        val stop1 = rng.range(1, 3) * 60.0
        periodEnd = 45 * 60.0 + stop1
        setRestart(Restart.KICKOFF, 0, L / 2, W / 2, 1.5)
        events.add(MatchEvent(0, EvType.KICKOFF, -1))
    }

    private fun place(i: Int, p: Player, slot: Slot) {
        playerAt[i] = p; slotAt[i] = slot; active[i] = true
        motivation[i] = motivationById[p.id] ?: 0.0
        energy[i] = (p.condition / 100.0).coerceIn(0.35, 1.0)
        val fam = fm.engine.ability.Ability.familiarity(p, slot.position)
        val famF = 0.72 + 0.28 * fam
        for (at in Attr.entries) a[i][at.ordinal] = ((p.attr(at) - 1.0) / 19.0 * famF).coerceIn(0.0, 1.0)
        val (wx, wy) = toWorld(teamOf(i), (slot.fx * 0.48 * L).coerceAtMost(L / 2 - 1), slot.fy * W)
        px[i] = wx; py[i] = wy; tx[i] = wx; ty[i] = wy
    }

    private fun at(i: Int, attr: Attr) = (a[i][attr.ordinal] * (1.0 + 0.05 * motivation[i])).coerceAtMost(1.0)

    /** Applies team-talk reactions (player id → value) to the given team, including the bench. */
    fun applyMotivation(team: Int, values: Map<Long, Double>) {
        motivationById.putAll(values)
        for (i in team * 11 until team * 11 + 11) playerAt[i]?.let { p -> values[p.id]?.let { motivation[i] = it } }
    }

    // ------------------------------------------------------------------ frames
    /** Team-frame u (0 = own goal line, 105 = opponent goal line) → world x. */
    private fun toWorld(team: Int, u: Double, v: Double): Pair<Double, Double> =
        if (dir[team] > 0) u to v else (L - u) to (W - v)

    private fun uOf(team: Int, x: Double) = if (dir[team] > 0) x else L - x
    private fun vOf(team: Int, y: Double) = if (dir[team] > 0) y else W - y
    private fun goalX(team: Int) = if (dir[team] > 0) L else 0.0 // goal team attacks
    private fun ownGoalX(team: Int) = if (dir[team] > 0) 0.0 else L

    private var restartCounter = 0
    /** Stable pseudo-random offset per player & restart (no RNG draw, so targets don't flicker). */
    private fun jit(i: Int, lo: Double, hi: Double): Double {
        val h = ((i * 7919L + restartCounter * 104729L + (tick / 50) * 31L) % 1000 + 1000) % 1000
        return lo + (hi - lo) * h / 1000.0
    }

    private fun dist(x1: Double, y1: Double, x2: Double, y2: Double) = hypot(x1 - x2, y1 - y2)

    // ------------------------------------------------------------------ public commands

    /** Queue a substitution (applied at the next stoppage). Returns false if not allowed. */
    fun substitute(team: Int, outIndex: Int, incomingId: Long): Boolean {
        if (subsUsed[team] + pendingSubs.count { it.first == team } >= sheets[team].maxSubs) return false
        if (teamOf(outIndex) != team || !active[outIndex]) return false
        if (sheets[team].bench.none { it.id == incomingId } || pm.containsKey(incomingId)) return false
        pendingSubs.add(Triple(team, outIndex, incomingId))
        if (phase == Phase.DEAD) applySubs()
        return true
    }

    fun setInstructions(team: Int, ins: TeamInstructions) {
        sheets[team].instructions = ins
        events.add(MatchEvent(minute, EvType.TACTIC, team))
    }

    fun subsRemaining(team: Int) = sheets[team].maxSubs - subsUsed[team] - pendingSubs.count { it.first == team }

    // ------------------------------------------------------------------ main loop

    /** Advance one tick. Returns false once the match is over. */
    fun step(): Boolean {
        if (phase == Phase.FINISHED) return false
        tick++
        clock += DT
        if (noTouchTime > 0) noTouchTime -= DT
        for (i in 0 until N) if (tackleCooldown[i] > 0) tackleCooldown[i] -= DT
        when (phase) {
            Phase.DEAD -> stepDead()
            Phase.LIVE -> stepLive()
            Phase.FINISHED -> {}
        }
        if (owner >= 0) stats[teamOf(owner)].possessionTicks++
        else stats[possessionTeam].possessionTicks++
        if (clock >= periodEnd && phase != Phase.FINISHED && shotPending == null) endPeriod()
        return phase != Phase.FINISHED
    }

    /** Simulate to the end (used for instant results). */
    fun runToEnd(): MatchResult {
        while (step()) { /* run */ }
        return result()
    }

    private fun stepDead() {
        deadTimer -= DT
        for (i in 0 until N) if (active[i] && (tick + i) % 2 == 0L) computeTarget(i)
        movePlayers()
        if (deadTimer <= 0) executeRestart()
    }

    private fun stepLive() {
        updateBall()
        if (phase != Phase.LIVE) return
        if (owner < 0 && shotPending == null) tryControl()
        if (owner >= 0) {
            decisionTimer -= DT
            if (decisionTimer <= 0) decide(owner)
        }
        if (phase != Phase.LIVE) return
        for (i in 0 until N) if (active[i] && (tick + i) % 3 == 0L) computeTarget(i)
        movePlayers()
        if (owner >= 0) challenges()
        aiSubstitutions()
    }

    // ------------------------------------------------------------------ ball physics

    private fun updateBall() {
        if (owner >= 0) {
            val o = owner
            val sp = hypot(vx[o], vy[o])
            val fx = if (sp > 0.1) vx[o] / sp else dir[teamOf(o)].toDouble()
            val fy = if (sp > 0.1) vy[o] / sp else 0.0
            ballX = px[o] + fx * 0.6; ballY = py[o] + fy * 0.6; ballZ = 0.0
            bvx = vx[o]; bvy = vy[o]; bvz = 0.0
        } else {
            val sp = hypot(bvx, bvy)
            if (ballZ > 0.0 || bvz > 0.0) {
                bvz -= 9.81 * DT
                val drag = 1.0 - 0.015 * DT * sp
                bvx *= drag; bvy *= drag
                ballZ += bvz * DT
                if (ballZ <= 0.0) {
                    ballZ = 0.0
                    if (abs(bvz) > 2.5) { bvz = -bvz * 0.45; bvx *= 0.75; bvy *= 0.75 } else bvz = 0.0
                }
            } else if (sp > 0.0) {
                val dec = (2.0 + 0.12 * sp) * DT
                val ns = (sp - dec).coerceAtLeast(0.0)
                bvx *= ns / sp; bvy *= ns / sp
            }
            ballX += bvx * DT; ballY += bvy * DT
        }
        if (shotPending != null) checkShotArrival()
        if (phase == Phase.LIVE && shotPending == null && owner < 0) checkOut()
    }

    private fun checkOut() {
        if ((ballY < 0 || ballY > W || ballX < 0 || ballX > L) && passer >= 0) dbg("pass_out")
        if (ballY < 0 || ballY > W) {
            val team = 1 - lastTouchTeam
            setRestart(Restart.THROW_IN, team, ballX.coerceIn(1.0, L - 1.0), if (ballY < 0) 0.3 else W - 0.3, 2.5)
            return
        }
        if (ballX < 0 || ballX > L) {
            val endX = if (ballX < 0) 0.0 else L
            val defending = if (ownGoalX(0) == endX) 0 else 1
            val speed = hypot(bvx, bvy)
            val ownTouch = lastTouchTeam == defending
            // A slow ball rolling toward goal is gathered by the keeper; most deflections go for a corner.
            val counts = if (ownTouch) rng.chance(0.15) && speed > 8 else speed > 14 && rng.chance(0.5)
            if (abs(ballY - W / 2) < GOAL_HALF && ballZ < GOAL_H && !counts) {
                if (ownTouch) {
                    stats[1 - defending].corners++
                    setRestart(Restart.CORNER, 1 - defending, if (endX == 0.0) 0.5 else L - 0.5, if (ballY < W / 2) 0.5 else W - 0.5, 4.5)
                } else setRestart(Restart.GOAL_KICK, defending, if (endX == 0.0) 5.5 else L - 5.5, W / 2, 3.0)
                return
            }
            if (abs(ballY - W / 2) < GOAL_HALF && ballZ < GOAL_H) {
                // Ball crossed the goal line between the posts (e.g. deflection / own goal).
                dbg("goal_lineCross")
                dbg("lc_" + (if (lastTouchTeam == defending) "own_" else "att_") + (slotAt.getOrNull(lastTouch)?.position?.name ?: "none") + "_z" + (ballZ * 2).toInt() + "_r" + restart.name)
                goalScored(1 - defending, if (lastTouchTeam == defending) -1 else lastTouch)
                return
            }
            if (lastTouchTeam == defending) {
                stats[1 - defending].corners++
                events.add(MatchEvent(minute, EvType.CORNER, 1 - defending))
                val cy = if (ballY < W / 2) 0.5 else W - 0.5
                setRestart(Restart.CORNER, 1 - defending, if (endX == 0.0) 0.5 else L - 0.5, cy, 4.5)
            } else {
                val gx = if (endX == 0.0) 5.5 else L - 5.5
                setRestart(Restart.GOAL_KICK, defending, gx, W / 2 + (if (ballY < W / 2) -9.0 else 9.0), 4.0)
            }
        }
    }

    private fun tryControl() {
        val speed = hypot(bvx, bvy)
        var best = -1; var bestD = 99.0
        for (i in 0 until N) {
            if (!active[i] || (i == noTouchIdx && noTouchTime > 0)) continue
            if (teamOf(i) != lastTouchTeam && triedIntercept[i] && speed > 4.0) continue
            val d = dist(px[i], py[i], ballX, ballY)
            val isGk = slotAt[i]?.position == Position.GK
            val reachZ = if (isGk && inOwnBox(i)) 2.8 else 1.9 + 0.5 * at(i, Attr.JUMPING)
            if (d < (if (isGk && inOwnBox(i)) 1.8 else 1.1) && ballZ < reachZ && d < bestD) { best = i; bestD = d }
        }
        if (best < 0) return
        val i = best
        val t = teamOf(i)
        val isGk = slotAt[i]?.position == Position.GK
        val high = ballZ > 1.1
        // Attacking header at goal from a cross.
        if (high && !isGk && t == lastTouchTeam && passIsCross && dist(px[i], py[i], goalX(t), W / 2) < 16) {
            header(i); return
        }
        val p = when {
            isGk && inOwnBox(i) -> 0.55 + 0.4 * at(i, Attr.HANDLING) - (if (t != lastTouchTeam) 0.0 else 0.0)
            i == passTarget -> 0.55 + 0.4 * at(i, Attr.FIRST_TOUCH) - 0.012 * speed - (if (high) 0.15 else 0.0)
            t == lastTouchTeam -> 0.35 + 0.35 * at(i, Attr.FIRST_TOUCH) - 0.01 * speed
            else -> (0.15 + 0.35 * at(i, Attr.ANTICIPATION) + 0.2 * at(i, Attr.POSITIONING) - 0.015 * speed) * (if (high) 0.6 else 1.0) * INTERCEPT_FACTOR
        }.coerceIn(0.05, 0.97)
        if (t != lastTouchTeam) triedIntercept[i] = true
        if (!rng.chance(p)) {
            // Failed touch: deflect the ball a little.
            if (rng.chance(0.3)) { bvx *= 0.5; bvy *= 0.5; bvx += rng.gaussian() * 2; bvy += rng.gaussian() * 2 }
            noTouchIdx = i; noTouchTime = 0.3
            return
        }
        // Defensive header/clearance in own third.
        if (high && t != lastTouchTeam && !isGk && uOf(t, px[i]) < 35) {
            clearance(i); return
        }
        gainPossession(i)
    }

    private fun inOwnBox(i: Int): Boolean {
        val t = teamOf(i)
        return uOf(t, px[i]) < 16.5 && abs(py[i] - W / 2) < 20.16
    }

    private fun gainPossession(i: Int) {
        val t = teamOf(i)
        if (passOffside && i == passTarget) {
            stats[t].offsides++
            events.add(MatchEvent(minute, EvType.OFFSIDE, t, playerAt[i]!!.id))
            passOffside = false
            setRestart(Restart.FREE_KICK, 1 - t, px[i], py[i], 2.5)
            return
        }
        if (passer >= 0 && teamOf(passer) == t && passTarget >= 0) { stats[t].passesCompleted++; dbg(if (i == passTarget) "pass_toTarget" else "pass_toMate") }
        else if (passer >= 0 && teamOf(passer) != t && passTarget >= 0) dbg("pass_intercepted")
        passer = -1
        if (t != possessionTeam) { possessionTeam = t; possessionChangeTime = clock }
        owner = i; lastTouch = i; lastTouchTeam = t
        passTarget = -1; passOffside = false; passIsCross = false
        val isGk = slotAt[i]?.position == Position.GK
        decisionTimer = if (isGk) 1.2 + rng.nextDouble() else reactionTime(i)
    }

    private fun reactionTime(i: Int): Double {
        val tempo = sheets[teamOf(i)].instructions.tempo
        return (REACTION_BASE + (1 - at(i, Attr.DECISIONS)) * 0.4 + (0.5 - tempo) * 0.3 + rng.nextDouble() * 0.25).coerceAtLeast(0.15)
    }

    // ------------------------------------------------------------------ decisions on the ball

    /** Value of having the ball at (x, y) for team t (≈ probability the possession ends in a goal). */
    private fun value(t: Int, x: Double, y: Double): Double {
        val u = uOf(t, x)
        val dg = dist(x, y, goalX(t), W / 2)
        if (dg > 30) return 0.004 + 0.012 * (u / L) + 0.25 * xgAt(t, x, y)
        val crowd = (1 - 0.3 * min(pressureAt(t, x, y), 2.0)) * openGoal(t, x, y)
        return 0.004 + 0.012 * (u / L) + 0.55 * xgAt(t, x, y) * crowd
    }

    private fun xgAt(t: Int, x: Double, y: Double): Double {
        val gx = goalX(t)
        val dx = abs(gx - x)
        val dg = hypot(dx, y - W / 2)
        if (dx < 0.5) return 0.02
        val a1 = atan2(W / 2 + GOAL_HALF - y, dx)
        val a2 = atan2(W / 2 - GOAL_HALF - y, dx)
        val angle = abs(a1 - a2)
        return XG_SCALE / (1.0 + exp(-(-2.2 + 1.9 * angle - 0.095 * dg)))
    }

    /** Fraction of the goal mouth still open, given outfield opponents inside the shooting triangle. */
    private fun openGoal(t: Int, x: Double, y: Double): Double {
        val gx = goalX(t)
        var open = 1.0
        for (k in 0 until N) {
            if (!active[k] || teamOf(k) == t || slotAt[k]?.position == Position.GK) continue
            val ku = uOf(t, px[k]); val su = uOf(t, x)
            if (ku <= su) continue
            val frac = (ku - su) / (uOf(t, gx) - su).coerceAtLeast(0.5)
            if (frac > 1) continue
            val halfW = GOAL_HALF * frac + 0.9 * (1 - frac)
            val cy = y + (W / 2 - y) * frac
            val off = abs(py[k] - cy)
            if (off < halfW + 0.6) open *= 0.55 + 0.35 * (off / (halfW + 0.6))
        }
        return open
    }

    private fun pressureOn(i: Int, x: Double = px[i], y: Double = py[i]): Double = pressureAt(teamOf(i), x, y)

    private fun pressureAt(t: Int, x: Double, y: Double): Double {
        var p = 0.0
        for (k in 0 until N) {
            if (!active[k] || teamOf(k) == t) continue
            val d = dist(px[k], py[k], x, y)
            if (d < 3.5) p += 1 - d / 3.5
        }
        return p
    }

    private fun nearestOpponentDist(t: Int, x: Double, y: Double): Double {
        var best = 99.0
        for (k in 0 until N) if (active[k] && teamOf(k) != t) best = min(best, dist(px[k], py[k], x, y))
        return best
    }

    private fun laneRisk(t: Int, x1: Double, y1: Double, x2: Double, y2: Double, lofted: Boolean): Double {
        val dx = x2 - x1; val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 < 1) return 0.0
        val len = sqrt(len2)
        var keep = 1.0
        for (k in 0 until N) {
            if (!active[k] || teamOf(k) == t) continue
            val s = ((px[k] - x1) * dx + (py[k] - y1) * dy) / len2
            if (s < 0.05 || s > 1.05) continue
            val cx = x1 + s * dx; val cy = y1 + s * dy
            val pd = dist(px[k], py[k], cx, cy)
            val reach = 1.0 + 0.06 * len * s
            var r = exp(-pd / reach) * (0.55 + 0.45 * at(k, Attr.ANTICIPATION))
            if (lofted) r *= if (s > 0.8) 0.6 else 0.15
            keep *= (1 - r * LANE_FACTOR)
        }
        return 1 - keep
    }

    private fun secondLastDefenderU(attTeam: Int): Double {
        val us = mutableListOf<Double>()
        for (k in 0 until N) if (active[k] && teamOf(k) != attTeam) us.add(uOf(attTeam, px[k]))
        us.sortDescending()
        return if (us.size >= 2) us[1] else L
    }

    private fun decide(i: Int) {
        val t = teamOf(i)
        val ins = sheets[t].instructions
        val isGk = slotAt[i]?.position == Position.GK
        val x = px[i]; val y = py[i]
        val pr = pressureOn(i)
        val dg = dist(x, y, goalX(t), W / 2)
        val noise = 0.06 + 0.3 * (1 - at(i, Attr.DECISIONS))
        fun jitter(u: Double) = u * (1 + rng.gaussian() * noise)

        var bestU = -1.0
        var bestKind = 0 // 1 shoot, 2 pass, 3 carry, 4 clear
        var bestTarget = -1
        var bestTx = 0.0; var bestTy = 0.0; var bestLofted = false; var bestCross = false

        // 1. Shoot
        if (!isGk && dg < 33) {
            val xg = xgAt(t, x, y) * (1 - 0.3 * min(pr, 2.0)) * openGoal(t, x, y)
            val skill = if (dg < 18) at(i, Attr.FINISHING) else 0.5 * at(i, Attr.LONG_SHOTS) + 0.5 * at(i, Attr.FINISHING)
            val bias = 1 + (ins.shootingRange - 0.5) * 0.8 + ins.mentality.value * 0.1
            val threshold = 0.02 + (0.5 - ins.shootingRange) * 0.02
            if (xg >= threshold || (dg < 12 && pr < 0.5)) {
                val u = jitter(xg * (0.75 + 0.5 * skill) * bias * SHOOT_WEIGHT)
                if (u > bestU) { bestU = u; bestKind = 1 }
            }
        }
        // 2. Pass / cross
        val vis = at(i, Attr.VISION)
        val acc = 0.55 * at(i, Attr.PASSING) + 0.25 * at(i, Attr.TECHNIQUE) + 0.2 * vis
        val loseCostBase = 0.5
        val keepBonus = 0.006 * (1 - ins.directness)
        val offsideU = secondLastDefenderU(t)
        for (j in 0 until N) {
            if (j == i || !active[j] || teamOf(j) != t) continue
            val jGk = slotAt[j]?.position == Position.GK
            if (jGk && pr < 0.8) continue
            // Lead the receiver (into space for runners).
            val lead = if (runTimer[j] > 0) 1.2 else 0.5
            var gx = px[j] + vx[j] * lead
            var gy = py[j] + vy[j] * lead
            gx = gx.coerceIn(1.0, L - 1.0); gy = gy.coerceIn(1.0, W - 1.0)
            val d = dist(x, y, gx, gy)
            if (d < 4 || d > 60) continue
            if (d > 22 + 30 * vis + 15 * ins.directness) continue
            val ju = uOf(t, gx)
            val wide = abs(gy - W / 2) > 12
            val inBox = ju > L - 16.5 && abs(gy - W / 2) < 20
            val crossing = wide.not() && inBox && abs(y - W / 2) > 14 && uOf(t, x) > L - 30
            val lofted = crossing || d > 30 || (d > 15 && laneRisk(t, x, y, gx, gy, false) > 0.5)
            val risk = laneRisk(t, x, y, gx, gy, lofted)
            val recvPress = (1 - nearestOpponentDist(t, gx, gy) / 4.0).coerceAtLeast(0.0)
            val accuracy = if (crossing) 0.6 * at(i, Attr.CROSSING) + 0.4 * at(i, Attr.TECHNIQUE) else acc
            var pS = (0.97 - d * (PASS_DIST_PENALTY - 0.004 * accuracy) - (if (lofted) 0.1 else 0.0)) *
                (1 - risk) * (1 - 0.3 * recvPress) * (1 - 0.1 * min(pr, 2.0))
            if (crossing) pS *= 0.55 + 0.3 * ins.crossing
            pS = pS.coerceIn(0.02, 0.98)
            val offside = ju > L / 2 && ju > uOf(t, ballX) && ju > offsideU + 0.3
            if (offside) pS *= 0.25
            val progress = (ju - uOf(t, x)) / L
            val v = value(t, gx, gy) * (if (crossing) 1.25 else 1.0) + keepBonus + ins.directness * 0.01 * progress
            val lossV = value(1 - t, gx, gy) * loseCostBase
            val u = jitter(pS * v - (1 - pS) * lossV)
            if (u > bestU) {
                bestU = u; bestKind = 2; bestTarget = j; bestTx = gx; bestTy = gy; bestLofted = lofted; bestCross = crossing
            }
        }
        // 3. Carry / dribble
        if (!isGk) {
            val dirX = if (dir[t] > 0) 1.0 else -1.0
            val carryX = (x + dirX * 6.0).coerceIn(7.0, L - 7.0)
            val carryY = (y + (W / 2 - y) * 0.15).coerceIn(1.0, W - 1.0)
            val space = nearestOpponentDist(t, carryX, carryY)
            val drib = 0.5 * at(i, Attr.DRIBBLING) + 0.25 * at(i, Attr.AGILITY) + 0.25 * at(i, Attr.BALANCE)
            val pKeep = if (space > 6) 0.93 else (0.35 + 0.5 * drib - 0.1 * pr).coerceIn(0.1, 0.9)
            val flair = 1 + 0.25 * (at(i, Attr.FLAIR) - 0.5)
            val u = jitter(pKeep * value(t, carryX, carryY) * flair - (1 - pKeep) * value(1 - t, x, y) * 0.5)
            if (u > bestU) { bestU = u; bestKind = 3; bestTx = carryX; bestTy = carryY }
        }
        // 4. Clearance under heavy pressure in own third
        if (uOf(t, x) < 25 && pr > 1.0 && bestU < 0.004) bestKind = 4

        dbg("decide_$bestKind")
        when (bestKind) {
            1 -> shoot(i)
            2 -> pass(i, bestTarget, bestTx, bestTy, bestLofted, bestCross, offsideU)
            3 -> { tx[i] = bestTx; ty[i] = bestTy; sprint[i] = true; decisionTimer = reactionTime(i) * 1.4 }
            4 -> clearance(i)
            else -> { decisionTimer = reactionTime(i) }
        }
    }

    private fun kick(i: Int, toX: Double, toY: Double, lofted: Boolean, power: Double = 1.0) {
        val d = dist(ballX, ballY, toX, toY).coerceAtLeast(1.0)
        val ang = atan2(toY - ballY, toX - ballX)
        if (lofted) {
            val vh = (10.0 + d * 0.38).coerceIn(12.0, 27.0) * power
            val tFlight = d * 0.92 / vh
            bvx = cos(ang) * vh; bvy = sin(ang) * vh; bvz = 0.5 * 9.81 * tFlight
            ballZ = 0.05
        } else {
            val v0 = sqrt(9.0 + 2 * 3.0 * d).coerceIn(7.0, 26.0) * power
            bvx = cos(ang) * v0; bvy = sin(ang) * v0; bvz = 0.0; ballZ = 0.0
        }
        owner = -1
        lastTouch = i; lastTouchTeam = teamOf(i)
        noTouchIdx = i; noTouchTime = 0.35
        java.util.Arrays.fill(triedIntercept, false)
    }

    private fun pass(i: Int, j: Int, gx: Double, gy: Double, lofted: Boolean, cross: Boolean, offsideU: Double) {
        val t = teamOf(i)
        val d = dist(px[i], py[i], gx, gy)
        val acc = if (cross) 0.6 * at(i, Attr.CROSSING) + 0.4 * at(i, Attr.TECHNIQUE)
        else 0.6 * at(i, Attr.PASSING) + 0.4 * at(i, Attr.TECHNIQUE)
        val fatigue = 1 + (1 - energy[i]) * 0.6
        val sigma = (0.02 + (1 - acc) * 0.08) * fatigue * (1 + 0.25 * min(pressureOn(i), 2.0))
        val err = rng.gaussian() * sigma
        val ang = atan2(gy - py[i], gx - px[i]) + err
        val lenErr = 1 + rng.gaussian() * sigma * 0.8
        val ex = (px[i] + cos(ang) * d * lenErr).coerceIn(-2.0, L + 2.0)
        val ey = (py[i] + sin(ang) * d * lenErr).coerceIn(-2.0, W + 2.0)
        stats[t].passes++
        val ju = uOf(t, px[j])
        passOffside = ju > L / 2 && ju > uOf(t, ballX) && ju > offsideU + 0.3
        passTarget = j; passer = i; passIsCross = cross
        lastPasser = i; lastPassTime = clock
        kick(i, ex, ey, lofted)
        // Receiver moves to meet the ball.
        tx[j] = ex; ty[j] = ey; sprint[j] = true
    }

    private fun clearance(i: Int) {
        val t = teamOf(i)
        val (cx, cy) = toWorld(t, uOf(t, px[i]) + rng.range(30.0, 50.0), rng.range(8.0, W - 8.0))
        passer = -1; passTarget = -1; passOffside = false; passIsCross = false
        kick(i, cx, cy, true, 1.05)
    }

    private fun shoot(i: Int, header: Boolean = false) {
        val t = teamOf(i)
        val x = px[i]; val y = py[i]
        val dg = dist(x, y, goalX(t), W / 2)
        val pr = pressureOn(i)
        val ins = sheets[t].instructions
        var xg = xgAt(t, x, y) * (1 - 0.3 * min(pr, 2.0)) * openGoal(t, x, y)
        if (header) xg *= 0.45 + 0.5 * (0.6 * at(i, Attr.HEADING) + 0.4 * at(i, Attr.JUMPING))
        stats[t].shots++
        stats[t].xg += xg
        dbg("shotDist", dg); dbg("shotPressure", pr); dbg("shots")
        if (debugLog != null && (debug["shots"] ?: 0.0) <= 6) {
            val defs = (0 until N).filter { active[it] && teamOf(it) != t }.joinToString(" ") { k ->
                "${slotAt[k]!!.position.code}(${"%.0f".format(uOf(t, px[k]))},${"%.0f".format(vOf(t, py[k]))})" }
            debugLog!!.add("SHOT by ${slotAt[i]!!.position.code} at u=${"%.0f".format(uOf(t, x))} v=${"%.0f".format(vOf(t, y))} restart=$restart prevOwnerTicks pr=${"%.2f".format(pr)} | $defs")
        }
        shooter = i
        shotIsPenalty = false
        // Block by a defender close to the shooting line
        val gk = goalkeeper(1 - t)
        for (k in 0 until N) {
            if (!active[k] || teamOf(k) == t || k == gk) continue
            val dk = dist(px[k], py[k], x, y)
            if (dk < 2.2 && uOf(t, px[k]) > uOf(t, x) && rng.chance(0.28 * (0.5 + at(k, Attr.POSITIONING) * 0.5) * (1 - ins.shootingRange * 0.2))) {
                events.add(MatchEvent(minute, EvType.BLOCKED, t, playerAt[i]!!.id))
                kick(i, px[k], py[k], false, 0.6)
                bvx = -bvx * 0.4 + rng.gaussian() * 3; bvy = rng.gaussian() * 5
                lastTouch = k; lastTouchTeam = teamOf(k)
                passer = -1; passTarget = -1
                return
            }
        }
        val skill = if (header) 0.6 * at(i, Attr.HEADING) + 0.4 * at(i, Attr.COMPOSURE)
        else if (dg < 18) 0.7 * at(i, Attr.FINISHING) + 0.3 * at(i, Attr.COMPOSURE)
        else 0.6 * at(i, Attr.LONG_SHOTS) + 0.25 * at(i, Attr.TECHNIQUE) + 0.15 * at(i, Attr.FINISHING)
        val fatigue = 1 - (1 - energy[i]) * 0.25
        val pOT = ((0.28 + 0.4 * skill - 0.006 * dg + 0.35 * xg) * fatigue * (1 - 0.12 * min(pr, 2.0))).coerceIn(0.1, 0.92)
        val gkSkill = if (gk >= 0) (if (dg < 12) 0.4 * at(gk, Attr.ONE_ON_ONES) + 0.4 * at(gk, Attr.REFLEXES) + 0.2 * at(gk, Attr.POSITIONING)
            else 0.4 * at(gk, Attr.REFLEXES) + 0.3 * at(gk, Attr.HANDLING) + 0.3 * at(gk, Attr.POSITIONING)) else 0.0
        val pGoalOT = (xg / pOT * FINISH_FACTOR * (1.25 - 0.5 * gkSkill) * (0.85 + 0.3 * skill)).coerceIn(0.03, 0.95)
        val onTarget = rng.chance(pOT)
        val gy = W / 2 + (if (rng.chance(0.5)) 1 else -1) * rng.range(0.8, GOAL_HALF - 0.4)
        val gxw = goalX(t)
        if (onTarget) {
            stats[t].onTarget++
            if (rng.chance(pGoalOT)) {
                shotPending = ShotOutcome.GOAL
                launchShot(i, gxw, gy, rng.range(0.3, 2.1))
            } else {
                stats[1 - t].saves++
                val r = rng.nextDouble()
                shotPending = when {
                    r < 0.5 + 0.3 * (if (gk >= 0) at(gk, Attr.HANDLING) else 0.0) -> ShotOutcome.SAVE_HOLD
                    r < 0.85 -> ShotOutcome.SAVE_CORNER
                    else -> ShotOutcome.SAVE_LOOSE
                }
                val gkY = if (gk >= 0) py[gk] else W / 2
                launchShot(i, gxw - dir[t] * 0.8, (gy + gkY) / 2, rng.range(0.3, 1.8))
            }
        } else {
            shotPending = if (rng.chance(0.6)) ShotOutcome.WIDE else ShotOutcome.OVER
            val wy = if (shotPending == ShotOutcome.WIDE) W / 2 + (if (rng.chance(0.5)) 1 else -1) * rng.range(GOAL_HALF + 0.5, GOAL_HALF + 6) else gy
            launchShot(i, gxw + dir[t] * 0.5, wy, if (shotPending == ShotOutcome.OVER) 3.2 else rng.range(0.2, 2.0))
        }
    }

    private fun launchShot(i: Int, toX: Double, toY: Double, height: Double) {
        val power = 20.0 + 10.0 * at(i, Attr.TECHNIQUE) * 0.5 + 6 * at(i, Attr.STRENGTH) * 0.5
        val d = dist(ballX, ballY, toX, toY).coerceAtLeast(1.0)
        val ang = atan2(toY - ballY, toX - ballX)
        bvx = cos(ang) * power; bvy = sin(ang) * power
        val tFlight = d / power
        bvz = (height + 0.5 * 9.81 * tFlight * tFlight) / tFlight
        ballZ = 0.2
        shotTick = tick
        owner = -1; lastTouch = i; lastTouchTeam = teamOf(i)
        noTouchIdx = i; noTouchTime = 1.0
        passer = -1; passTarget = -1; passOffside = false; passIsCross = false
    }

    private fun checkShotArrival() {
        val t = lastTouchTeam
        val gx = goalX(t)
        val gk = goalkeeper(1 - t)
        val reached = abs(ballX - gx) < 1.2 || ballX < -0.5 || ballX > L + 0.5 || ballY < -1 || ballY > W + 1 || tick - shotTick > 30
        if (!reached) return
        val outcome = shotPending ?: return
        shotPending = null
        val sid = if (shooter >= 0) playerAt[shooter]!!.id else -1
        when (outcome) {
            ShotOutcome.GOAL -> { dbg("goal_shot"); goalScored(t, shooter) }
            ShotOutcome.SAVE_HOLD -> {
                events.add(MatchEvent(minute, EvType.SAVE, 1 - t, if (gk >= 0) playerAt[gk]!!.id else -1, sid))
                if (gk >= 0) {
                    px[gk] = ballX.coerceIn(1.5, L - 1.5); py[gk] = ballY.coerceIn(W / 2 - 6, W / 2 + 6)
                    vx[gk] = 0.0; vy[gk] = 0.0
                    bvx = 0.0; bvy = 0.0; bvz = 0.0; ballZ = 0.0
                    gainPossession(gk)
                } else setRestart(Restart.GOAL_KICK, 1 - t, if (gx == L) L - 5.5 else 5.5, W / 2, 3.0)
            }
            ShotOutcome.SAVE_CORNER -> {
                events.add(MatchEvent(minute, EvType.SAVE, 1 - t, if (gk >= 0) playerAt[gk]!!.id else -1, sid))
                stats[t].corners++
                setRestart(Restart.CORNER, t, if (gx == L) L - 0.5 else 0.5, if (rng.chance(0.5)) 0.5 else W - 0.5, 4.5)
            }
            ShotOutcome.SAVE_LOOSE -> {
                events.add(MatchEvent(minute, EvType.SAVE, 1 - t, if (gk >= 0) playerAt[gk]!!.id else -1, sid))
                bvx = -dir[t] * rng.range(5.0, 11.0); bvy = rng.gaussian() * 6; bvz = 0.0; ballZ = 0.0
                ballX = gx - dir[t] * 1.5
                if (gk >= 0) { lastTouch = gk; lastTouchTeam = 1 - t }
            }
            ShotOutcome.WIDE, ShotOutcome.OVER -> {
                if (!shotIsPenalty) events.add(MatchEvent(minute, EvType.MISS, t, sid))
                setRestart(Restart.GOAL_KICK, 1 - t, if (gx == L) L - 5.5 else 5.5, W / 2 + rng.range(-9.0, 9.0), 4.0)
            }
        }
        if (shotIsPenalty && outcome != ShotOutcome.GOAL) {
            events.add(MatchEvent(minute, EvType.PEN_MISS, t, sid))
        }
        shotIsPenalty = false
    }

    private fun header(i: Int) {
        shoot(i, header = true)
    }

    private fun goalkeeper(team: Int): Int {
        for (k in team * 11 until team * 11 + 11) if (active[k] && slotAt[k]?.position == Position.GK) return k
        return -1
    }

    private fun goalScored(team: Int, scorerIdx: Int) {
        goals[team]++
        val scorerId = if (scorerIdx >= 0 && teamOf(scorerIdx) == team) playerAt[scorerIdx]!!.id else -1
        var assistId = -1L
        if (scorerId >= 0) {
            pm[scorerId]?.let { it.goals++; it.rating += 1.0 }
            if (lastPasser >= 0 && lastPasser != scorerIdx && teamOf(lastPasser) == team && clock - lastPassTime < 12) {
                assistId = playerAt[lastPasser]!!.id
                pm[assistId]?.let { it.assists++; it.rating += 0.6 }
            }
        }
        val type = if (shotIsPenalty) EvType.PEN_GOAL else EvType.GOAL
        events.add(MatchEvent(minute, type, team, scorerId, assistId, "${goals[0]}-${goals[1]}"))
        // Concession penalty for defenders & keeper
        for (k in (1 - team) * 11 until (1 - team) * 11 + 11) {
            val p = playerAt[k] ?: continue
            if (active[k] && (slotAt[k]?.position?.line == Line.DEF || slotAt[k]?.position == Position.GK)) pm[p.id]?.let { it.rating -= 0.25 }
        }
        shotIsPenalty = false
        shotPending = null
        setRestart(Restart.KICKOFF, 1 - team, L / 2, W / 2, 4.0)
    }

    // ------------------------------------------------------------------ duels

    private fun challenges() {
        val o = owner
        val t = teamOf(o)
        for (k in 0 until N) {
            if (!active[k] || teamOf(k) == t || tackleCooldown[k] > 0) continue
            val d = dist(px[k], py[k], px[o], py[o])
            if (d > 1.4) continue
            val ins = sheets[1 - t].instructions
            val rate = 0.12 * (0.6 + 0.4 * at(k, Attr.AGGRESSION) + 0.4 * ins.pressing)
            if (!rng.chance(rate)) continue
            tackleCooldown[k] = 0.7
            val def = 0.6 * at(k, Attr.TACKLING) + 0.2 * at(k, Attr.POSITIONING) + 0.2 * at(k, Attr.STRENGTH)
            val att = 0.6 * at(o, Attr.DRIBBLING) + 0.2 * at(o, Attr.AGILITY) + 0.2 * at(o, Attr.STRENGTH)
            val pWin = (0.45 + 0.45 * (def - att) + 0.08 * (energy[o] - energy[k])).coerceIn(0.15, 0.8)
            dbg("tackleAttempts")
            if (rng.chance(pWin)) {
                stats[1 - t].tackles++
                pm[playerAt[k]!!.id]?.let { it.rating += 0.08 }
                if (rng.chance(0.65)) gainPossession(k)
                else { owner = -1; bvx = rng.gaussian() * 4; bvy = rng.gaussian() * 4; lastTouch = k; lastTouchTeam = 1 - t; passer = -1; passTarget = -1 }
                return
            }
            val inOwnBoxTackle = uOf(t, px[o]) > L - 16.5 && abs(py[o] - W / 2) < 20.16
            val booked = (pm[playerAt[k]!!.id]?.yellow ?: 0) >= 1
            val pFoul = (0.09 + 0.16 * at(k, Attr.AGGRESSION) - 0.07 * at(k, Attr.TACKLING)).coerceIn(0.03, 0.35) *
                (if (inOwnBoxTackle) 0.22 else 1.0) * (if (booked) 0.6 else 1.0)
            if (rng.chance(pFoul)) { foul(k, o); return }
            tackleCooldown[k] = 1.2 // beaten
        }
    }

    private fun foul(k: Int, victim: Int) {
        val t = teamOf(k)
        val fouler = playerAt[k]!!
        stats[t].fouls++
        pm[fouler.id]?.let { it.rating -= 0.1 }
        events.add(MatchEvent(minute, EvType.FOUL, t, fouler.id))
        val lastMan = uOf(1 - t, px[victim]) > secondLastDefenderU(1 - t) - 1
        val m = pm.getValue(fouler.id)
        // Booked players are more careful; referees are more lenient with a second yellow.
        val pYellow = (0.11 + 0.2 * at(k, Attr.AGGRESSION) + (if (lastMan) 0.15 else 0.0)) * (if (m.yellow >= 1) 0.45 else 1.0)
        if (rng.chance(if (lastMan) 0.02 else 0.003)) sendOff(k, direct = true)
        else if (rng.chance(pYellow)) {
            m.yellow++; stats[t].yellows++
            m.rating -= 0.3
            events.add(MatchEvent(minute, EvType.YELLOW, t, fouler.id))
            if (m.yellow >= 2) sendOff(k, direct = false)
        }
        // Injury to the fouled player
        val v = playerAt[victim]!!
        if (rng.chance(0.012 * (0.5 + v.injuryProneness / 20.0))) injure(victim)
        val inBox = uOf(1 - t, px[victim]) > L - 16.5 && abs(py[victim] - W / 2) < 20.16
        owner = -1
        if (inBox) setRestart(Restart.PENALTY, 1 - t, if (goalX(1 - t) == L) L - 11 else 11.0, W / 2, 4.0)
        else setRestart(Restart.FREE_KICK, 1 - t, px[victim], py[victim], 3.0)
    }

    private fun sendOff(k: Int, direct: Boolean) {
        val t = teamOf(k)
        val p = playerAt[k]!!
        val m = pm.getValue(p.id)
        m.red = true; m.rating -= 1.5
        stats[t].reds++
        events.add(MatchEvent(minute, EvType.RED, t, p.id))
        removeFromPitch(k)
    }

    private fun removeFromPitch(k: Int) {
        val p = playerAt[k] ?: return
        pm[p.id]?.let { it.minutes = minuteNow() - (minuteOn[p.id] ?: 0.0).toInt(); it.energy = energy[k] }
        active[k] = false
        px[k] = -10.0; py[k] = -10.0
        if (owner == k) owner = -1
    }

    private fun injure(i: Int) {
        val p = playerAt[i] ?: return
        val m = pm.getValue(p.id)
        if (m.injuryDays > 0) return
        val inj = rng.pick(fm.engine.world.NamePools.injuries)
        m.injuryDays = (inj.second * rng.range(0.5, 1.6)).toInt().coerceAtLeast(2)
        m.injuryName = inj.first
        events.add(MatchEvent(minute, EvType.INJURY, teamOf(i), p.id))
        val t = teamOf(i)
        val bench = sheets[t].bench.filter { !pm.containsKey(it.id) }
        if (subsRemaining(t) > 0 && bench.isNotEmpty() && (setup.userTeam != t)) {
            val pos = slotAt[i]!!.position
            val best = bench.maxByOrNull { fm.engine.ability.Ability.effective(it, pos) }!!
            pendingSubs.add(Triple(t, i, best.id))
        } else if (setup.userTeam == t && subsRemaining(t) > 0 && bench.isNotEmpty()) {
            // Human team: substitute automatically too, so play is never stuck with an injured player.
            val pos = slotAt[i]!!.position
            val best = bench.maxByOrNull { fm.engine.ability.Ability.effective(it, pos) }!!
            pendingSubs.add(Triple(t, i, best.id))
        }
    }

    private fun minuteNow(): Int = when (period) { 1 -> (clock / 60).toInt(); 2 -> 45 + (clock / 60).toInt(); 3 -> 90 + (clock / 60).toInt(); else -> 105 + (clock / 60).toInt() }

    private fun aiSubstitutions() {
        if (tick % 600 != 0L) return
        val m = minuteNow()
        if (m < 55) return
        for (t in 0..1) {
            if (t == setup.userTeam) continue
            if (subsRemaining(t) <= 0) continue
            // Replace the most tired outfield player if clearly fatigued.
            var worst = -1; var worstE = 0.64
            for (k in t * 11 until t * 11 + 11) {
                if (!active[k] || slotAt[k]?.position == Position.GK) continue
                if (pendingSubs.any { it.second == k }) continue
                if (energy[k] < worstE) { worstE = energy[k]; worst = k }
            }
            if (worst < 0 && m >= 75 && rng.chance(0.3)) {
                worst = (t * 11 until t * 11 + 11).filter { active[it] && slotAt[it]?.position != Position.GK }.minByOrNull { energy[it] } ?: -1
            }
            if (worst < 0) continue
            val pos = slotAt[worst]!!.position
            val bench = sheets[t].bench.filter { !pm.containsKey(it.id) && pendingSubs.none { s -> s.third == it.id } && it.position != Position.GK }
            val best = bench.maxByOrNull { fm.engine.ability.Ability.effective(it, pos) } ?: continue
            pendingSubs.add(Triple(t, worst, best.id))
        }
    }

    private fun applySubs() {
        val it = pendingSubs.iterator()
        while (it.hasNext()) {
            val (t, k, inId) = it.next()
            it.remove()
            if (!active[k]) continue
            val incoming = sheets[t].bench.firstOrNull { b -> b.id == inId } ?: continue
            if (subsUsed[t] >= sheets[t].maxSubs) continue
            val out = playerAt[k]!!
            pm[out.id]?.let { m -> m.minutes = minuteNow() - (minuteOn[out.id] ?: 0.0).toInt(); m.energy = energy[k] }
            subsUsed[t]++
            val slot = slotAt[k]!!
            val ox = px[k]; val oy = py[k]
            place(k, incoming, slot)
            px[k] = ox; py[k] = oy
            pm[incoming.id] = PlayerMatch(incoming.id, t)
            minuteOn[incoming.id] = minuteNow().toDouble()
            events.add(MatchEvent(minute, EvType.SUB, t, incoming.id, out.id))
        }
    }

    // ------------------------------------------------------------------ off-ball positioning

    private fun computeTarget(i: Int) {
        if (i == owner) return
        val t = teamOf(i)
        val slot = slotAt[i] ?: return
        val ins = sheets[t].instructions
        val ballU = uOf(t, ballX)
        val ballV = vOf(t, ballY)
        val attacking = (owner >= 0 && teamOf(owner) == t) || (owner < 0 && possessionTeam == t)
        val isGk = slot.position == Position.GK
        var u: Double; var v: Double
        sprint[i] = false
        if (phase == Phase.DEAD) { deadBallTarget(i); return }

        if (isGk) {
            // Goalkeeper on the line between ball and goal centre.
            val depth = if (ballU < 52) 1.5 + 4.0 * at(i, Attr.RUSHING_OUT) * (1 - ballU / 52) else 8.0 + 6.0 * at(i, Attr.RUSHING_OUT)
            u = depth.coerceAtMost(16.0)
            v = W / 2 + (ballV - W / 2) * (if (ballU < 40) 0.12 else 0.05)
            // Come for loose balls in the box
            if (owner < 0 && ballU < 16.5 && abs(ballV - W / 2) < 20 && isNearestToBall(i)) { u = ballU; v = ballV; sprint[i] = true }
            setTargetFromFrame(i, t, u, v)
            return
        }
        val m = ins.mentality.value
        if (attacking) {
            val back = (ballU - 38 + 8 * m).coerceIn(10.0, 58.0)
            val front = (back + 52 + 6 * m).coerceIn(42.0, 99.0)
            u = back + (slot.fx - 0.16) / 0.62 * (front - back)
            v = W / 2 + (slot.fy - 0.5) * W * (0.72 + 0.36 * ins.width)
            when (slot.role) {
                Role.WING_BACK -> u += 12
                Role.FULL_BACK -> if (ballU > 45) u += 6
                Role.INVERTED_FULL_BACK -> { v = W / 2 + (v - W / 2) * 0.45; u += 4 }
                Role.BOX_TO_BOX -> if (ballU > 55) u += 8
                Role.INSIDE_FORWARD -> { v = W / 2 + (v - W / 2) * 0.7; u += 3 }
                Role.FALSE_NINE -> u -= 8
                Role.ADVANCED_PLAYMAKER, Role.DEEP_PLAYMAKER -> u -= 2
                else -> {}
            }
            // Attackers stay onside, and time runs beyond the line.
            val line = secondLastDefenderU(t)
            if (slot.position.line == Line.ATT || slot.role == Role.BOX_TO_BOX) {
                val runner = owner >= 0 && teamOf(owner) == t
                val timing = 0.15 + 0.5 * at(i, Attr.OFF_THE_BALL) + 0.2 * at(i, Attr.ANTICIPATION)
                if (runner && decisionTimer < timing && at(i, Attr.OFF_THE_BALL) > rng.nextDouble() * 1.2 && ballU > 30) {
                    runTimer[i] = 1.5
                }
                if (runTimer[i] > 0) { runTimer[i] -= DT * 3; u = max(u, line + 6.0); sprint[i] = true }
                else u = min(u, line - 0.7)
            }
            // Crash the box when the ball is wide in the final third.
            if (ballU > L - 32 && abs(ballV - W / 2) > 14 && (slot.position == Position.ST || slot.position == Position.AMC ||
                    (slot.position.line == Line.ATT && sign(slot.fy - 0.5) != sign(ballV - W / 2)))) {
                u = L - jit(i, 6.0, 12.0); v = W / 2 + jit(i + 5, -6.0, 6.0); sprint[i] = true
            }
            // Support the ball carrier: two closest teammates offer short options in space.
            if (owner >= 0 && teamOf(owner) == t && supportRank(i) < 2) {
                val (su, sv) = supportSpot(i, t)
                u = (u + su) / 2; v = (v + sv) / 2
            }
            // Loose ball chase
            if (owner < 0 && chaseRank(i) == 0) { u = uOf(t, landingX()); v = vOf(t, landingY()); sprint[i] = true }
        } else {
            val lineBase = 14 + 24 * ins.defensiveLine + (if (ins.offsideTrap) 3 else 0)
            val defLine = min(lineBase + (ballU - 52) * 0.35, ballU - 3.0).coerceIn(6.0, 52.0)
            val length = 26 + 10 * (1 - ins.pressing)
            u = defLine + (slot.fx - 0.16) / 0.62 * length
            v = W / 2 + (slot.fy - 0.5) * W * (0.5 + 0.3 * ins.defensiveWidth) + (ballV - W / 2) * 0.38
            // Pressing
            val engageU = 30 + 55 * ins.engagement
            val recentLoss = clock - possessionChangeTime < 4 && ins.counterPress > 0.5
            val pressers = 1 + (if (ins.pressing > 0.6) 1 else 0) + (if (recentLoss) 1 else 0)
            val rank = chaseRank(i)
            val oppBallU = ballU // our frame: distance from our goal
            if (rank < pressers && (oppBallU < engageU || oppBallU < 35)) {
                u = ballU - 1.0; v = ballV
                sprint[i] = at(i, Attr.WORK_RATE) + ins.pressing > 0.6 || oppBallU < 30
            }
            // Marking: goal-side of the nearest attacker in zone.
            if (rank >= pressers && slot.position.line != Line.ATT) {
                var bestK = -1; var bestD = 12.0
                for (k in 0 until N) {
                    if (!active[k] || teamOf(k) == t) continue
                    val ku = uOf(t, px[k]); val kv = vOf(t, py[k])
                    val d = hypot(ku - u, kv - v)
                    if (d < bestD) { bestD = d; bestK = k }
                }
                if (bestK >= 0) {
                    val w = 0.3 + 0.55 * ins.tightMarking * (0.5 + 0.5 * at(i, Attr.MARKING))
                    val ku = uOf(t, px[bestK]) - 1.8; val kv = vOf(t, py[bestK])
                    u = u + (ku - u) * w; v = v + (kv - v) * w
                }
            }
            // Protect the box when the ball is close: defenders drop and narrow.
            if (ballU < 32 && slot.position.line == Line.DEF) {
                u = min(u, 4.0 + ballU * 0.35)
                v = v + (W / 2 - v) * 0.35
            }
            if (ballU < 32 && slot.position == Position.DM) { u = min(u, 10.0 + ballU * 0.3); v = v + (W / 2 - v) * 0.4 }
            // Stay goal-side of the ball.
            if ((slot.position.line == Line.DEF || slot.position == Position.DM) && ballU < 62) u = min(u, ballU - 1.5)
            // Danger zone: the two nearest players always engage the carrier (one tackles, one covers).
            if (owner >= 0 && teamOf(owner) != t && ballU < 42) {
                val r = chaseRank(i)
                if (r == 0) { u = ballU - 0.8; v = ballV; sprint[i] = true }
                else if (r == 1) {
                    val gu = 0.0; val gv = W / 2
                    u = ballU + (gu - ballU) * 0.25; v = ballV + (gv - ballV) * 0.25; sprint[i] = true
                }
            }
            if (owner < 0 && chaseRank(i) == 0) { u = uOf(t, landingX()); v = vOf(t, landingY()); sprint[i] = true }
        }
        setTargetFromFrame(i, t, u.coerceIn(1.0, L - 1.0), v.coerceIn(1.0, W - 1.0))
    }

    private fun setTargetFromFrame(i: Int, t: Int, u: Double, v: Double) {
        val (x, y) = toWorld(t, u, v)
        tx[i] = x; ty[i] = y
    }

    private fun landingX(): Double {
        if (ballZ <= 0.0 && bvz <= 0.0) return (ballX + bvx * 0.6).coerceIn(0.0, L)
        val tl = (bvz + sqrt(bvz * bvz + 2 * 9.81 * ballZ)) / 9.81
        return (ballX + bvx * tl).coerceIn(0.0, L)
    }

    private fun landingY(): Double {
        if (ballZ <= 0.0 && bvz <= 0.0) return (ballY + bvy * 0.6).coerceIn(0.0, W)
        val tl = (bvz + sqrt(bvz * bvz + 2 * 9.81 * ballZ)) / 9.81
        return (ballY + bvy * tl).coerceIn(0.0, W)
    }

    private fun isNearestToBall(i: Int): Boolean {
        val d = dist(px[i], py[i], ballX, ballY)
        for (k in 0 until N) if (k != i && active[k] && dist(px[k], py[k], ballX, ballY) < d) return false
        return true
    }

    /** 0 = closest player of his team to the (predicted) ball. */
    private fun chaseRank(i: Int): Int {
        val t = teamOf(i)
        val lx = if (owner >= 0) ballX else landingX()
        val ly = if (owner >= 0) ballY else landingY()
        val d = dist(px[i], py[i], lx, ly)
        var r = 0
        for (k in t * 11 until t * 11 + 11) {
            if (k == i || !active[k] || slotAt[k]?.position == Position.GK) continue
            if (dist(px[k], py[k], lx, ly) < d) r++
        }
        return r
    }

    private fun supportRank(i: Int): Int {
        val t = teamOf(i)
        val d = dist(px[i], py[i], ballX, ballY)
        var r = 0
        for (k in t * 11 until t * 11 + 11) {
            if (k == i || k == owner || !active[k] || slotAt[k]?.position == Position.GK) continue
            if (dist(px[k], py[k], ballX, ballY) < d) r++
        }
        return r
    }

    private fun supportSpot(i: Int, t: Int): Pair<Double, Double> {
        val bu = uOf(t, ballX); val bv = vOf(t, ballY)
        var bestU = bu; var bestV = bv; var bestScore = -1.0
        for (k in 0 until 6) {
            val ang = -PI / 2 + k * PI / 5 + (if (i % 2 == 0) 0.2 else -0.2)
            val r = 11.0
            val cu = (bu + cos(ang) * r).coerceIn(2.0, L - 2.0)
            val cv = (bv + sin(ang) * r).coerceIn(2.0, W - 2.0)
            val (wx, wy) = toWorld(t, cu, cv)
            val space = nearestOpponentDist(t, wx, wy)
            val score = space + (cu - bu) * 0.15
            if (score > bestScore) { bestScore = score; bestU = cu; bestV = cv }
        }
        return bestU to bestV
    }

    private fun deadBallTarget(i: Int) {
        val t = teamOf(i)
        val slot = slotAt[i]!!
        val isGk = slot.position == Position.GK
        val ru = uOf(t, restartX); val rv = vOf(t, restartY)
        var u: Double; var v: Double
        when (restart) {
            Restart.KICKOFF -> {
                u = (slot.fx * 0.48 * L).coerceAtMost(L / 2 - 1); v = slot.fy * W
                if (t == restartTeam && slot.position == Position.ST) { u = L / 2 - 0.5; v = W / 2 + (if (i % 2 == 0) 0.0 else 2.0) }
            }
            Restart.CORNER, Restart.FREE_KICK -> {
                val attackingSide = t == restartTeam
                if (restart == Restart.CORNER || ru > L - 35) {
                    if (attackingSide) {
                        u = if (slot.position.line == Line.DEF && slot.position != Position.DC) 60.0 else L - jit(i, 5.0, 14.0)
                        v = W / 2 + jit(i + 3, -9.0, 9.0)
                        if (slot.position.line == Line.MID && slot.role == Role.ANCHOR) { u = L - 22; v = W / 2 }
                    } else {
                        u = if (slot.position.line == Line.ATT && slot.position != Position.ST) 30.0 else jit(i, 3.0, 12.0)
                        v = W / 2 + jit(i + 7, -8.0, 8.0)
                    }
                    if (isGk) { u = if (attackingSide) 10.0 else 1.0; v = W / 2 }
                } else {
                    u = (slot.fx * 0.8 * L * 0.7 + (if (attackingSide) ru * 0.4 else 0.0)).coerceIn(2.0, L - 3)
                    v = slot.fy * W
                }
                // Defenders at least 9.15 m from the ball
                if (!attackingSide) {
                    val d = hypot(u - (L - ru), v - (W - rv))
                    if (d < 9.5) { u -= 9.5 - d }
                }
            }
            Restart.PENALTY -> {
                u = if (t == restartTeam) L - 20 else 18.0; v = W / 2 + (slot.fy - 0.5) * 30
                if (isGk) { u = if (t == restartTeam) 30.0 else 0.3; v = W / 2 }
            }
            else -> { computeTargetLiveFallback(i); return }
        }
        setTargetFromFrame(i, t, u.coerceIn(0.5, L - 0.5), v.coerceIn(0.5, W - 0.5))
    }

    private fun computeTargetLiveFallback(i: Int) {
        val saved = phase
        phase = Phase.LIVE
        computeTarget(i)
        phase = saved
    }

    // ------------------------------------------------------------------ movement & fatigue

    private fun movePlayers() {
        for (i in 0 until N) {
            if (!active[i]) continue
            if (i == owner) {
                // Carrier moves toward his carry target with the ball (slower than running free).
                if (dist(px[i], py[i], tx[i], ty[i]) < 1.0) {
                    tx[i] = (px[i] + dir[teamOf(i)] * 3.0).coerceIn(1.0, L - 1.0); ty[i] = py[i]
                }
            }
            val pace = at(i, Attr.PACE); val accA = at(i, Attr.ACCELERATION)
            val fat = 0.68 + 0.32 * energy[i]
            var maxV = (5.6 + 3.6 * pace) * fat
            if (i == owner) maxV *= 0.68 + 0.17 * at(i, Attr.DRIBBLING)
            val dx = tx[i] - px[i]; val dy = ty[i] - py[i]
            val d = hypot(dx, dy)
            val far = d > 9
            val desired = when {
                d < 0.3 -> 0.0
                sprint[i] || far || i == owner -> maxV
                else -> min(maxV * 0.55, d * 1.2)
            }
            val dvx = if (d > 0.01) dx / d * desired - vx[i] else -vx[i]
            val dvy = if (d > 0.01) dy / d * desired - vy[i] else -vy[i]
            val accMax = (3.0 + 4.0 * accA) * DT
            val dvl = hypot(dvx, dvy)
            val sc = if (dvl > accMax) accMax / dvl else 1.0
            vx[i] += dvx * sc; vy[i] += dvy * sc
            px[i] += vx[i] * DT; py[i] += vy[i] * DT
            if (i == owner) { px[i] = px[i].coerceIn(1.0, L - 1.0); py[i] = py[i].coerceIn(0.8, W - 0.8) }
            else { px[i] = px[i].coerceIn(-1.0, L + 1.0); py[i] = py[i].coerceIn(-1.0, W + 1.0) }
            // Fatigue
            val ratio = hypot(vx[i], vy[i]) / (5.6 + 3.6 * pace)
            val drain = 0.000025 / (0.6 + 0.8 * at(i, Attr.STAMINA)) * (0.7 + 0.6 * (1 - at(i, Attr.NATURAL_FITNESS)))
            energy[i] -= (ratio * ratio * 1.6 + 0.08) * drain
            if (ratio < 0.25) energy[i] += 0.000002
            energy[i] = energy[i].coerceIn(0.15, 1.0)
        }
        // Simple separation so players don't stack.
        if (tick % 2 == 0L) for (i in 0 until N) {
            if (!active[i]) continue
            for (k in i + 1 until N) {
                if (!active[k]) continue
                val dx = px[k] - px[i]; val dy = py[k] - py[i]
                val d2 = dx * dx + dy * dy
                if (d2 < 0.64 && d2 > 1e-6) {
                    val d = sqrt(d2); val push = (0.8 - d) * 0.5
                    px[i] -= dx / d * push; py[i] -= dy / d * push
                    px[k] += dx / d * push; py[k] += dy / d * push
                }
            }
        }
    }

    // ------------------------------------------------------------------ restarts

    private fun setRestart(type: Restart, team: Int, x: Double, y: Double, delay: Double) {
        phase = Phase.DEAD
        restartCounter++
        possessionTeam = team
        restart = type; restartTeam = team
        restartX = x; restartY = y
        deadTimer = delay
        owner = -1
        bvx = 0.0; bvy = 0.0; bvz = 0.0
        ballX = x; ballY = y; ballZ = 0.0
        passTarget = -1; passOffside = false; passIsCross = false; passer = -1
        shotPending = null
        applySubs()
    }

    private fun executeRestart() {
        val t = restartTeam
        phase = Phase.LIVE
        ballX = restartX; ballY = restartY; ballZ = 0.0
        val taker: Int = when (restart) {
            Restart.GOAL_KICK -> goalkeeper(t).takeIf { it >= 0 } ?: nearestOfTeam(t, restartX, restartY)
            Restart.PENALTY -> bestOf(t) { at(it, Attr.PENALTY) * 0.7 + at(it, Attr.COMPOSURE) * 0.3 }
            Restart.CORNER -> bestOf(t) { at(it, Attr.CORNERS) * 0.7 + at(it, Attr.CROSSING) * 0.3 }
            Restart.FREE_KICK -> if (uOf(t, restartX) > L - 32) bestOf(t) { at(it, Attr.FREE_KICK) } else nearestOfTeam(t, restartX, restartY)
            else -> nearestOfTeam(t, restartX, restartY)
        }
        if (taker < 0) { setRestart(Restart.KICKOFF, t, L / 2, W / 2, 1.0); return }
        px[taker] = restartX - dir[t] * 0.6; py[taker] = restartY
        vx[taker] = 0.0; vy[taker] = 0.0
        possessionTeam = t
        when (restart) {
            Restart.PENALTY -> {
                shotIsPenalty = true
                val gk = goalkeeper(1 - t)
                val skill = 0.7 * at(taker, Attr.PENALTY) + 0.3 * at(taker, Attr.COMPOSURE)
                val gkS = if (gk >= 0) 0.5 * at(gk, Attr.REFLEXES) + 0.5 * at(gk, Attr.ONE_ON_ONES) else 0.0
                val pGoal = (0.74 + 0.16 * (skill - 0.5) - 0.12 * (gkS - 0.5)).coerceIn(0.5, 0.92)
                stats[t].shots++; stats[t].xg += 0.76
                shooter = taker
                val gy = W / 2 + (if (rng.chance(0.5)) 1 else -1) * rng.range(1.0, GOAL_HALF - 0.5)
                if (rng.chance(pGoal)) { stats[t].onTarget++; shotPending = ShotOutcome.GOAL; launchShot(taker, goalX(t), gy, rng.range(0.3, 1.6)) }
                else if (rng.chance(0.65)) { stats[t].onTarget++; stats[1 - t].saves++; shotPending = ShotOutcome.SAVE_CORNER; launchShot(taker, goalX(t) - dir[t] * 0.8, gy, 1.0) }
                else { shotPending = ShotOutcome.WIDE; launchShot(taker, goalX(t) + dir[t] * 0.5, W / 2 + GOAL_HALF + 1.5, 1.5) }
                shotIsPenalty = true
            }
            Restart.CORNER -> {
                // Deliver into the box toward the best aerial attacker.
                val target = bestOf(t, exclude = taker) { if (uOf(t, px[it]) > L - 20) at(it, Attr.HEADING) + at(it, Attr.JUMPING) else 0.0 }
                val (ax, ay) = toWorld(t, L - rng.range(5.0, 12.0), W / 2 + rng.range(-7.0, 7.0))
                passTarget = target; passer = taker; passIsCross = true; passOffside = false
                stats[t].passes++
                lastPasser = taker; lastPassTime = clock
                val acc = at(taker, Attr.CORNERS)
                kick(taker, ax + rng.gaussian() * (3 - 2 * acc), ay + rng.gaussian() * (3 - 2 * acc), true)
            }
            Restart.FREE_KICK -> {
                val dg = dist(restartX, restartY, goalX(t), W / 2)
                if (dg < 28 && abs(restartY - W / 2) < 18 && rng.chance(0.6)) {
                    owner = taker
                    val save = at(taker, Attr.LONG_SHOTS)
                    // Direct free kick uses the free-kick attribute as finishing.
                    a[taker][Attr.LONG_SHOTS.ordinal] = at(taker, Attr.FREE_KICK)
                    shoot(taker)
                    a[taker][Attr.LONG_SHOTS.ordinal] = save
                } else {
                    gainPossession(taker); decisionTimer = 0.4
                }
            }
            else -> { gainPossession(taker); decisionTimer = if (restart == Restart.KICKOFF) 0.3 else 0.6 }
        }
    }

    private fun nearestOfTeam(t: Int, x: Double, y: Double): Int {
        var best = -1; var bd = 1e9
        for (k in t * 11 until t * 11 + 11) {
            if (!active[k] || slotAt[k]?.position == Position.GK) continue
            val d = dist(px[k], py[k], x, y)
            if (d < bd) { bd = d; best = k }
        }
        return best
    }

    private fun bestOf(t: Int, exclude: Int = -1, score: (Int) -> Double): Int {
        var best = -1; var bs = -1e9
        for (k in t * 11 until t * 11 + 11) {
            if (!active[k] || k == exclude || slotAt[k]?.position == Position.GK) continue
            val s = score(k)
            if (s > bs) { bs = s; best = k }
        }
        return best
    }

    // ------------------------------------------------------------------ periods

    private fun endPeriod() {
        when (period) {
            1 -> {
                events.add(MatchEvent(45, EvType.HALFTIME, -1, score = "${goals[0]}-${goals[1]}"))
                period = 2; clock = 0.0
                periodEnd = 45 * 60.0 + rng.range(2, 5) * 60.0
                dir[0] = -dir[0]; dir[1] = -dir[1]
                for (i in 0 until N) if (active[i]) energy[i] = (energy[i] + 0.06).coerceAtMost(1.0)
                setRestart(Restart.KICKOFF, 1, L / 2, W / 2, 2.0)
                resetFormationPositions()
                events.add(MatchEvent(45, EvType.SECOND_HALF, -1))
            }
            2 -> {
                val aggH = goals[0] + setup.firstLeg.first; val aggA = goals[1] + setup.firstLeg.second
                if (setup.needsWinner && aggH == aggA) {
                    extraTimePlayed = true
                    events.add(MatchEvent(90, EvType.EXTRA_TIME, -1))
                    period = 3; clock = 0.0; periodEnd = 15 * 60.0
                    dir[0] = -dir[0]; dir[1] = -dir[1]
                    sheets[0].maxSubs.let { }
                    subsUsed[0] = (subsUsed[0] - 1).coerceAtLeast(0); subsUsed[1] = (subsUsed[1] - 1).coerceAtLeast(0)
                    setRestart(Restart.KICKOFF, 0, L / 2, W / 2, 2.0)
                    resetFormationPositions()
                } else finish()
            }
            3 -> {
                period = 4; clock = 0.0; periodEnd = 15 * 60.0
                dir[0] = -dir[0]; dir[1] = -dir[1]
                setRestart(Restart.KICKOFF, 1, L / 2, W / 2, 1.5)
                resetFormationPositions()
            }
            else -> {
                val aggH = goals[0] + setup.firstLeg.first; val aggA = goals[1] + setup.firstLeg.second
                if (aggH == aggA) {
                    events.add(MatchEvent(120, EvType.PENALTIES, -1))
                    val (h, a2) = Penalties.shootout(rng, onPitchSheet(0), onPitchSheet(1))
                    penHome = h; penAway = a2
                }
                finish()
            }
        }
    }

    private fun onPitchSheet(t: Int): TeamSheet {
        val st = (t * 11 until t * 11 + 11).filter { active[it] }.map { playerAt[it]!! to slotAt[it]!! }
        val s = sheets[t]
        return TeamSheet(s.clubId, s.clubName, s.color, s.altColor, st, emptyList(), s.instructions, s.formation, 0)
    }

    private fun resetFormationPositions() {
        for (i in 0 until N) {
            if (!active[i]) continue
            val s = slotAt[i]!!
            val (x, y) = toWorld(teamOf(i), (s.fx * 0.48 * L).coerceAtMost(L / 2 - 1), s.fy * W)
            px[i] = x; py[i] = y; vx[i] = 0.0; vy[i] = 0.0
        }
    }

    private fun finish() {
        phase = Phase.FINISHED
        owner = -1
        val end = minuteNow().coerceAtLeast(90)
        for (i in 0 until N) {
            val p = playerAt[i] ?: continue
            if (!active[i]) continue
            pm[p.id]?.let { it.minutes = end - (minuteOn[p.id] ?: 0.0).toInt(); it.energy = energy[i] }
        }
        // Ratings: team result, passing and involvement.
        for ((id, m) in pm) {
            val t = m.team
            val gd = goals[t] - goals[1 - t]
            m.rating += 0.12 * gd.coerceIn(-3, 3) + rng.gaussian() * 0.25
            if (goals[1 - t] == 0) {
                val p = (0 until N).firstOrNull { playerAt[it]?.id == id }
                val line = p?.let { slotAt[it]?.position?.line }
                if (line == Line.DEF || line == Line.GK) m.rating += 0.5
            }
            if (m.minutes < 15 && !m.started) m.rating = (m.rating + 6.5) / 2
            m.rating = m.rating.coerceIn(3.0, 10.0)
        }
        for (s in 0..1) {
            val gk = (s * 11 until s * 11 + 11).firstOrNull { slotAt[it]?.position == Position.GK }
            if (gk != null) playerAt[gk]?.let { p -> pm[p.id]?.let { it.rating = (it.rating + stats[s].saves * 0.2).coerceAtMost(10.0) } }
        }
        events.add(MatchEvent(end, EvType.FULLTIME, -1, score = "${goals[0]}-${goals[1]}"))
    }

    fun result(): MatchResult {
        val r = MatchResult(goals[0], goals[1], extraTimePlayed, penHome, penAway, events.toList(), stats, pm, Detail.FULL)
        r.winner = QuickSim.decideWinner(goals[0] + setup.firstLeg.first, goals[1] + setup.firstLeg.second, penHome, penAway)
        return r
    }
}
