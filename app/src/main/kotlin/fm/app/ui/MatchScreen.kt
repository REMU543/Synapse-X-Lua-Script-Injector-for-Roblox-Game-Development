package fm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fm.app.GameViewModel
import fm.core.*
import fm.engine.match.EvType
import fm.engine.match.MatchEngine2D
import fm.engine.match.MatchEvent

/**
 * Live 2D match. The engine is stepped from the frame clock (simulation never runs inside the
 * draw call); the Canvas only reads positions. Portrait layout draws the pitch vertically.
 */
@Composable
fun MatchScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val e = vm.engine ?: return
    val w = vm.world ?: return
    val user = e.setup.userTeam
    var speed by remember { mutableIntStateOf(2) }
    var paused by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var htShown by remember { mutableStateOf(false) }
    var subOut by remember { mutableIntStateOf(-1) }
    val speeds = listOf(5, 10, 20, 40, 80) // match seconds per real second
    BackHandler { paused = true }
    LaunchedEffect(e) {
        var last = 0L
        var acc = 0.0
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && !paused && e.phase != MatchEngine2D.Phase.FINISHED) {
                    acc += (now - last) / 1e9 * speeds[speed]
                    var n = 0
                    while (acc >= MatchEngine2D.DT && n < 400) { e.step(); acc -= MatchEngine2D.DT; n++ }
                    if (!htShown && e.events.any { it.type == EvType.HALFTIME }) { htShown = true; paused = true }
                }
                last = now
                frame++
            }
        }
    }
    val home = e.sheets[0]; val away = e.sheets[1]
    Column(Modifier.fillMaxSize()) {
        // Scoreboard
        @Suppress("UNUSED_VARIABLE") val f = frame
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(home.clubName, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${e.goals[0]} - ${e.goals[1]}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SmallText(if (e.phase == MatchEngine2D.Phase.FINISHED) vm.t("Selesai", "Full time") else e.clockLabel)
                if (e.setup.firstLeg != (0 to 0)) SmallText(vm.t("Agregat ", "Agg ") + "${e.goals[0] + e.setup.firstLeg.first}-${e.goals[1] + e.setup.firstLeg.second}")
            }
            Text(away.clubName, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        // Pitch
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(68f / 105f).padding(6.dp)) {
                @Suppress("UNUSED_EXPRESSION") frame
                drawPitch()
                val sx = size.width / 68f; val sy = size.height / 105f
                fun pt(x: Double, y: Double) = Offset((y * sx).toFloat(), (size.height - x * sy).toFloat())
                for (i in 0 until 22) {
                    if (!e.active[i]) continue
                    val t = e.teamOf(i)
                    val c = Color(if (t == 0) home.color else away.color)
                    val c2 = Color(if (t == 0) home.altColor else away.altColor)
                    val isGk = e.slotAt[i]?.position == Position.GK
                    val center = pt(e.px[i], e.py[i])
                    drawCircle(Color.Black.copy(alpha = 0.35f), 7.5f * density / 2.6f + 4f, center + Offset(1.5f, 1.5f))
                    drawCircle(if (isGk) Color(0xFFFFEB3B) else c, 7.5f * density / 2.6f + 3f, center)
                    drawCircle(if (c2 == c) Color.White else c2, 7.5f * density / 2.6f + 3f, center, style = Stroke(2.5f))
                    if (i == e.owner) drawCircle(Color.White, 7.5f * density / 2.6f + 7f, center, style = Stroke(1.5f))
                }
                val b = pt(e.ballX, e.ballY)
                val lift = (e.ballZ * 4).toFloat()
                drawCircle(Color.Black.copy(alpha = 0.4f), 4.5f, b + Offset(lift * 0.6f, lift * 0.6f))
                drawCircle(Color.White, 4.5f + lift * 0.25f, b)
            }
        }
        // Controls
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.phase == MatchEngine2D.Phase.FINISHED) {
                Button(onClick = { vm.finishMatch() }, Modifier.weight(1f)) { Text(vm.t("Lanjutkan", "Continue")) }
            } else {
                FilledTonalButton(onClick = { paused = !paused }) { Text(if (paused) "▶" else "❚❚") }
                Spacer(Modifier.width(6.dp))
                for ((i, label) in listOf("½x", "1x", "2x", "4x", "8x").withIndex()) {
                    FilterChip(speed == i, { speed = i }, { Text(label) }, Modifier.padding(horizontal = 2.dp))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.finishMatch() }) { Text(vm.t("Hasil", "Result")) }
            }
        }
        TabRow(tab) {
            for ((i, t) in listOf(vm.t("Komentar", "Commentary"), vm.t("Statistik", "Stats"), vm.t("Ganti", "Subs"), vm.t("Taktik", "Tactics")).withIndex())
                Tab(tab == i, { tab = i }, text = { Text(t, maxLines = 1) })
        }
        Box(Modifier.height(190.dp).fillMaxWidth()) {
            when (tab) {
                0 -> LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                    items(e.events.filter { it.type !in setOf(EvType.FOUL) }.takeLast(60).reversed()) { ev ->
                        val txt = commentary(vm, w, e, ev) ?: return@items
                        val bold = ev.type == EvType.GOAL || ev.type == EvType.PEN_GOAL || ev.type == EvType.RED
                        Text("${ev.minute}'  $txt", style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                1 -> StatsPanel(vm, e)
                2 -> if (user < 0) SmallText("-") else SubsPanel(vm, e, user, subOut) { subOut = it }
                else -> if (user >= 0) Column(Modifier.padding(8.dp)) {
                    Text(vm.t("Mentalitas", "Mentality"), style = MaterialTheme.typography.labelLarge)
                    ChipRow(Mentality.entries.map { it.label(vm.lang) }, e.sheets[user].instructions.mentality.ordinal) {
                        e.setInstructions(user, e.sheets[user].instructions.copy(mentality = Mentality.entries[it]))
                    }
                    LabeledSlider(vm.t("Pressing", "Pressing"), e.sheets[user].instructions.pressing, vm.t("Rendah", "Low"), vm.t("Tinggi", "High")) {
                        e.sheets[user].instructions = e.sheets[user].instructions.copy(pressing = it)
                    }
                    LabeledSlider(vm.t("Garis pertahanan", "Defensive line"), e.sheets[user].instructions.defensiveLine, vm.t("Dalam", "Deep"), vm.t("Tinggi", "High")) {
                        e.sheets[user].instructions = e.sheets[user].instructions.copy(defensiveLine = it)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPitch() {
    val stripes = 10
    for (i in 0 until stripes) drawRect(if (i % 2 == 0) Pal.pitch else Pal.pitchStripe, Offset(0f, size.height * i / stripes), Size(size.width, size.height / stripes))
    val l = Color.White.copy(alpha = 0.75f)
    val sx = size.width / 68f; val sy = size.height / 105f
    drawRect(l, style = Stroke(2f))
    drawLine(l, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
    drawCircle(l, 9.15f * sx, Offset(size.width / 2, size.height / 2), style = Stroke(2f))
    for (top in listOf(true, false)) {
        val y0 = if (top) 0f else size.height - 16.5f * sy
        drawRect(l, Offset((34 - 20.16f) * sx, y0), Size(40.32f * sx, 16.5f * sy), style = Stroke(2f))
        val y1 = if (top) 0f else size.height - 5.5f * sy
        drawRect(l, Offset((34 - 9.16f) * sx, y1), Size(18.32f * sx, 5.5f * sy), style = Stroke(2f))
        val gy = if (top) -1.5f * sy else size.height
        drawRect(Color.White, Offset((34 - 3.66f) * sx, gy), Size(7.32f * sx, 1.5f * sy))
    }
}

private fun commentary(vm: GameViewModel, w: World, e: MatchEngine2D, ev: MatchEvent): String? {
    fun name(id: Long) = w.players[id]?.shortName ?: "?"
    fun team(t: Int) = if (t in 0..1) e.sheets[t].clubName else ""
    val l = vm.lang
    return when (ev.type) {
        EvType.KICKOFF -> I18n.t(l, "ev_kickoff")
        EvType.HALFTIME -> I18n.t(l, "ev_halftime", ev.score)
        EvType.SECOND_HALF -> I18n.t(l, "ev_secondhalf")
        EvType.FULLTIME -> I18n.t(l, "ev_fulltime", ev.score)
        EvType.GOAL -> I18n.t(l, "ev_goal", name(ev.playerId), team(ev.team)) + (if (ev.otherId >= 0) " (" + vm.t("assist ", "assist ") + name(ev.otherId) + ")" else "")
        EvType.PEN_GOAL -> I18n.t(l, "ev_penalty_goal", name(ev.playerId))
        EvType.PEN_MISS -> I18n.t(l, "ev_penalty_miss", name(ev.playerId))
        EvType.SAVE -> I18n.t(l, "ev_save", name(ev.playerId))
        EvType.MISS -> I18n.t(l, "ev_miss", name(ev.playerId))
        EvType.BLOCKED -> I18n.t(l, "ev_blocked", name(ev.playerId))
        EvType.YELLOW -> I18n.t(l, "ev_yellow", name(ev.playerId))
        EvType.RED -> I18n.t(l, "ev_red", name(ev.playerId))
        EvType.OFFSIDE -> I18n.t(l, "ev_offside", name(ev.playerId))
        EvType.CORNER -> I18n.t(l, "ev_corner", team(ev.team))
        EvType.SUB -> I18n.t(l, "ev_sub", team(ev.team), name(ev.playerId), name(ev.otherId))
        EvType.INJURY -> I18n.t(l, "ev_injury", name(ev.playerId))
        EvType.TACTIC -> I18n.t(l, "ev_tactic", team(ev.team))
        EvType.EXTRA_TIME -> vm.t("Perpanjangan waktu!", "Extra time!")
        EvType.PENALTIES -> vm.t("Adu penalti!", "Penalty shoot-out!")
        else -> null
    }
}

@Composable
private fun StatsPanel(vm: GameViewModel, e: MatchEngine2D) {
    val s = e.stats
    val totalPoss = (s[0].possessionTicks + s[1].possessionTicks).coerceAtLeast(1)
    val rows = listOf(
        vm.t("Penguasaan bola", "Possession") to ("${100 * s[0].possessionTicks / totalPoss}%" to "${100 * s[1].possessionTicks / totalPoss}%"),
        vm.t("Tembakan", "Shots") to ("${s[0].shots}" to "${s[1].shots}"),
        vm.t("Tepat sasaran", "On target") to ("${s[0].onTarget}" to "${s[1].onTarget}"),
        "xG" to ("%.2f".format(s[0].xg) to "%.2f".format(s[1].xg)),
        vm.t("Umpan (sukses)", "Passes (completed)") to ("${s[0].passes} (${s[0].passesCompleted})" to "${s[1].passes} (${s[1].passesCompleted})"),
        vm.t("Tekel", "Tackles") to ("${s[0].tackles}" to "${s[1].tackles}"),
        vm.t("Pelanggaran", "Fouls") to ("${s[0].fouls}" to "${s[1].fouls}"),
        vm.t("Kartu kuning/merah", "Yellow/red") to ("${s[0].yellows}/${s[0].reds}" to "${s[1].yellows}/${s[1].reds}"),
        vm.t("Sepak pojok", "Corners") to ("${s[0].corners}" to "${s[1].corners}"),
        vm.t("Offside", "Offsides") to ("${s[0].offsides}" to "${s[1].offsides}"),
    )
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        items(rows) { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(v.first, Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(k, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                Text(v.second, Modifier.width(80.dp), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SubsPanel(vm: GameViewModel, e: MatchEngine2D, team: Int, out: Int, onOut: (Int) -> Unit) {
    Row(Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            item { SmallText(vm.t("Di lapangan (pilih keluar)", "On pitch (pick off)"), bold = true) }
            items((team * 11 until team * 11 + 11).filter { e.active[it] }) { i ->
                val p = e.playerAt[i]!!
                Text("${e.slotAt[i]!!.position.label(vm.lang)} ${p.shortName} ${(e.energy[i] * 100).toInt()}%",
                    Modifier.fillMaxWidth().clickable { onOut(i) }.background(if (out == i) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent).padding(4.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SmallText(vm.t("Cadangan ", "Bench ") + "(${e.subsRemaining(team)})", bold = true) }
            items(e.sheets[team].bench.filter { !e.pm.containsKey(it.id) }) { p ->
                Text("${p.position.label(vm.lang)} ${p.shortName}", Modifier.fillMaxWidth().clickable(enabled = out >= 0) {
                    if (e.substitute(team, out, p.id)) { vm.toast = vm.t("Pergantian dijadwalkan", "Substitution queued"); onOut(-1) }
                }.padding(4.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
