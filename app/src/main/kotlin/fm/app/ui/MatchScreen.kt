package fm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.app.GameViewModel
import fm.app.GameViewModel.MdStage
import fm.app.Screen
import fm.core.*
import fm.engine.dynamics.Press
import fm.engine.dynamics.TeamTalk
import fm.engine.game.Assistant
import fm.engine.match.EvType
import fm.engine.match.MatchEngine2D
import fm.engine.match.MatchEvent

/** The whole match-day flow for the user's match. */
@Composable
fun MatchDayScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val md = vm.matchDay ?: run { vm.navigate(Screen.Home, true); return }
    BackHandler { /* match day must be completed */ }
    Column(Modifier.fillMaxSize()) {
        StageBar(vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.mdStage) {
                MdStage.PRESS_PRE, MdStage.PRESS_POST -> PressScreen(vm)
                MdStage.PREVIEW -> PreviewScreen(vm, md)
                MdStage.TALK_PRE -> TeamTalkScreen(vm, TeamTalk.Stage.PRE) { vm.kickOff() }
                MdStage.LIVE -> LiveMatchScreen(vm)
                MdStage.REPORT -> ReportScreen(vm)
                MdStage.TALK_FULL -> TeamTalkScreen(vm, TeamTalk.Stage.FULL) { vm.talkResult = emptyList(); vm.world?.pendingPress?.let { vm.mdStage = MdStage.PRESS_POST } ?: vm.endMatchDay() }
            }
        }
    }
}

@Composable
private fun StageBar(vm: GameViewModel) {
    val md = vm.matchDay ?: return
    val fm = LocalFm.current
    val steps = listOf(MdStage.PRESS_PRE to vm.t("Pers", "Press"), MdStage.PREVIEW to vm.t("Briefing", "Briefing"), MdStage.TALK_PRE to "Team Talk",
        MdStage.LIVE to vm.t("Pertandingan", "Match"), MdStage.REPORT to vm.t("Laporan", "Report"), MdStage.TALK_FULL to "Team Talk", MdStage.PRESS_POST to vm.t("Pers", "Press"))
    Row(Modifier.fillMaxWidth().background(fm.topBar).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val w = vm.world!!
        Text("${w.club(md.fixture.homeId).name}  vs  ${w.club(md.fixture.awayId).name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        for ((i, s) in steps.withIndex()) {
            val active = vm.mdStage == s.first
            val done = vm.mdStage.ordinal > s.first.ordinal
            Text(s.second, fontSize = 11.sp, color = when { active -> MaterialTheme.colorScheme.primary; done -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) },
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            if (i < steps.size - 1) Text("  ›  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- press conference

@Composable
private fun PressScreen(vm: GameViewModel) {
    val w = vm.world ?: return
    val press = w.pendingPress
    Row(Modifier.fillMaxSize().padding(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel(if (vm.mdStage == MdStage.PRESS_PRE) vm.t("Konferensi Pers Pra-Laga", "Pre-Match Press Conference") else vm.t("Konferensi Pers Pasca-Laga", "Post-Match Press Conference")) {
                if (press == null) {
                    Text(vm.t("Konferensi pers selesai.", "The press conference is over."))
                } else {
                    val q = press.questions.getOrNull(press.answered)
                    if (q != null) {
                        SmallText(vm.t("Pertanyaan ", "Question ") + "${press.answered + 1}/${press.questions.size}", MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("“" + I18n.t(vm.lang, q.key, q.args) + "”", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 10.dp))
                        for (a in Press.Answer.entries) OutlinedButton(onClick = { vm.answerPress(q, a) }, Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(a.label(vm.lang)) }
                    }
                }
                vm.pressReaction?.let { Spacer(Modifier.height(8.dp)); Text("📰 $it", color = MaterialTheme.colorScheme.secondary) }
            }
        }
        Column(Modifier.width(260.dp).padding(start = 8.dp)) {
            Panel(vm.t("Tentang Pers", "About the Press")) {
                SmallText(vm.t("Jawaban Anda memengaruhi moral pemain, ekspektasi media dan dewan.", "Your answers affect player morale, media expectations and the board."))
                MeterRow(vm.t("Ekspektasi publik", "Media hype"), w.mediaHype)
            }
            Spacer(Modifier.weight(1f))
            val done = w.pendingPress == null
            Button(onClick = {
                if (done) { vm.pressReaction = null; if (vm.mdStage == MdStage.PRESS_PRE) vm.mdStage = MdStage.PREVIEW else vm.endMatchDay() }
                else vm.skipPress()
            }, Modifier.fillMaxWidth()) {
                Text(if (done) vm.t("Lanjut", "Continue") else vm.t("Kirim asisten (lewati)", "Send assistant (skip)"))
            }
        }
    }
}

// ---------------------------------------------------------------- preview

@Composable
private fun PreviewScreen(vm: GameViewModel, md: fm.engine.game.MatchDay) {
    val w = vm.world ?: return
    val pv = md.preview
    val c = vm.career!!
    val setup = remember(vm.version) { c.setupFor(md.fixture) }
    val own = if (md.userTeam == 0) setup.home else setup.away
    val opp = if (md.userTeam == 0) setup.away else setup.home
    Row(Modifier.fillMaxSize().padding(4.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel(vm.t("Susunan Pemain Kita", "Our Line-up") + " · ${own.formation.name}", action = {
                TextButton(onClick = { vm.navigate(Screen.Tactics) }) { Text(vm.t("Ubah taktik", "Edit tactics")) }
            }) {
                for ((p, slot) in own.starters) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Box(Modifier.width(40.dp)) { SmallText(slot.position.label(vm.lang), bold = true) }
                    Box(Modifier.weight(1f)) { SmallText(p.name) }
                    SmallText("${p.condition}%", if (p.condition < 80) LocalFm.current.warn else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp)); SmallText(moraleLabel(p.morale))
                }
                SmallText(vm.t("Cadangan: ", "Bench: ") + own.bench.joinToString { it.shortName }, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel(vm.t("Analisis Lawan: ", "Opposition: ") + pv.opponent.name) {
                KeyValue(vm.t("Kompetisi", "Competition"), pv.competition.name(vm.lang))
                KeyValue(vm.t("Venue", "Venue"), if (pv.home) vm.t("Kandang", "Home") else vm.t("Tandang", "Away"))
                if (pv.competition.type == CompType.LEAGUE) KeyValue(vm.t("Posisi (kita / lawan)", "Position (us / them)"), "${pv.ownPosition} / ${pv.opponentPosition}")
                Row(verticalAlignment = Alignment.CenterVertically) { SmallText(vm.t("Performa lawan: ", "Their form: ")); FormDots(pv.opponentForm) }
                KeyValue(vm.t("Formasi perkiraan", "Expected formation"), pv.opponentFormation)
                KeyValue(vm.t("Mentalitas perkiraan", "Expected mentality"), pv.opponentMentality.label(vm.lang))
                SmallText(vm.t("Pemain kunci: ", "Key players: ") + pv.opponentKeyPlayers.joinToString { it.name }, MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(vm.t("Prediksi", "Prediction"), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp))) {
                    Box(Modifier.weight(pv.winPct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Pal.win), contentAlignment = Alignment.Center) { Text("${pv.winPct}%", fontSize = 10.sp, color = Color.White) }
                    Box(Modifier.weight(pv.drawPct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Pal.draw), contentAlignment = Alignment.Center) { Text("${pv.drawPct}%", fontSize = 10.sp, color = Color.White) }
                    Box(Modifier.weight(pv.lossPct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Pal.loss), contentAlignment = Alignment.Center) { Text("${pv.lossPct}%", fontSize = 10.sp, color = Color.White) }
                }
                Spacer(Modifier.height(6.dp))
                Text(vm.t("Susunan perkiraan lawan", "Predicted opposition XI"), style = MaterialTheme.typography.labelLarge)
                for ((p, slot) in opp.starters) Row { Box(Modifier.width(40.dp)) { SmallText(slot.position.label(vm.lang), bold = true) }; SmallText(p.name) }
            }
        }
        Column(Modifier.width(300.dp).verticalScroll(rememberScrollState())) {
            Panel(vm.t("Saran Asisten", "Assistant Advice")) {
                for ((k, args) in pv.adviceKeys) Text("• " + I18n.t(vm.lang, k, args), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                if (pv.ownUnavailable.isNotEmpty()) SmallText(vm.t("Absen: ", "Unavailable: ") + pv.ownUnavailable.joinToString { it.shortName }, LocalFm.current.bad)
            }
            Button(onClick = { vm.mdStage = MdStage.TALK_PRE }, Modifier.fillMaxWidth().padding(6.dp)) { Text(vm.t("Ke ruang ganti (Team Talk)", "To the dressing room (Team Talk)")) }
            OutlinedButton(onClick = { vm.kickOff() }, Modifier.fillMaxWidth().padding(horizontal = 6.dp)) { Text(vm.t("Lewati, langsung kick-off", "Skip, kick off")) }
        }
    }
}

// ---------------------------------------------------------------- team talk

@Composable
private fun TeamTalkScreen(vm: GameViewModel, stage: TeamTalk.Stage, onDone: () -> Unit) {
    val md = vm.matchDay ?: return
    val w = vm.world ?: return
    val suggestion = md.suggestedTone(stage)
    Row(Modifier.fillMaxSize().padding(4.dp)) {
        Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
            Panel(when (stage) { TeamTalk.Stage.PRE -> vm.t("Team Talk Pra-Laga", "Pre-Match Team Talk"); TeamTalk.Stage.HALF -> vm.t("Team Talk Turun Minum", "Half-Time Team Talk"); TeamTalk.Stage.FULL -> vm.t("Team Talk Akhir Laga", "Full-Time Team Talk") }) {
                if (stage != TeamTalk.Stage.PRE) md.engine?.let { SmallText(vm.t("Skor: ", "Score: ") + "${it.goals[0]} - ${it.goals[1]}") }
                    ?: md.report?.let { SmallText(vm.t("Skor: ", "Score: ") + "${it.result.homeGoals} - ${it.result.awayGoals}") }
                SmallText(vm.t("Saran asisten: ", "Assistant suggests: ") + suggestion.label(vm.lang), MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                if (vm.talkResult.isEmpty()) {
                    for (t in TeamTalk.Tone.entries) OutlinedButton(onClick = { vm.teamTalk(stage, t) }, Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(t.label(vm.lang)) }
                } else Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(vm.t("Lanjut", "Continue")) }
                if (vm.talkResult.isEmpty()) TextButton(onClick = onDone) { Text(vm.t("Lewati team talk", "Skip team talk")) }
            }
        }
        Column(Modifier.weight(1f)) {
            Panel(vm.t("Reaksi Pemain", "Player Reactions")) {
                if (vm.talkResult.isEmpty()) SmallText(vm.t("Pilih nada bicara. Pemain bereaksi sesuai situasi dan kepribadian masing-masing.", "Pick a tone. Players react according to the situation and their personality."))
                LazyColumn {
                    items(vm.talkResult) { r ->
                        val p = w.players[r.playerId] ?: return@items
                        val color = when { r.value > 0.15 -> LocalFm.current.good; r.value > -0.15 -> MaterialTheme.colorScheme.onSurfaceVariant; else -> LocalFm.current.bad }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Box(Modifier.width(40.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
                            Box(Modifier.weight(1f)) { SmallText(p.name) }
                            SmallText(r.label(vm.lang), color, bold = true)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- live match (landscape)

@Composable
private fun LiveMatchScreen(vm: GameViewModel) {
    val e = vm.engine ?: return
    val w = vm.world ?: return
    val md = vm.matchDay ?: return
    val user = e.setup.userTeam
    var speed by remember { mutableIntStateOf(2) }
    var paused by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var htDone by remember { mutableStateOf(false) }
    var showHalfTalk by remember { mutableStateOf(false) }
    var subOut by remember { mutableIntStateOf(-1) }
    var showSubs by remember { mutableStateOf(false) }
    val speeds = listOf(5, 10, 20, 40, 80)
    LaunchedEffect(e) {
        var last = 0L
        var acc = 0.0
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && !paused && !showHalfTalk && e.phase != MatchEngine2D.Phase.FINISHED) {
                    acc += (now - last) / 1e9 * speeds[speed]
                    var n = 0
                    while (acc >= MatchEngine2D.DT && n < 400) { e.step(); acc -= MatchEngine2D.DT; n++ }
                    if (!htDone && e.events.any { it.type == EvType.HALFTIME }) { htDone = true; showHalfTalk = true; vm.talkResult = emptyList() }
                }
                last = now
                frame++
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val f = frame
    val home = e.sheets[0]; val away = e.sheets[1]
    val fm = LocalFm.current
    Row(Modifier.fillMaxSize()) {
        // Left: our team with live condition & ratings
        val compact = LocalCompact.current
        Column(Modifier.width(if (compact) 170.dp else 220.dp).fillMaxHeight().background(fm.panel).padding(6.dp)) {
            Text(e.sheets[user.coerceAtLeast(0)].clubName, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            LazyColumn(Modifier.weight(1f)) {
                items((user.coerceAtLeast(0) * 11 until user.coerceAtLeast(0) * 11 + 11).filter { e.active[it] }) { i ->
                    val p = e.playerAt[i]!!
                    val m = e.pm[p.id]
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(30.dp)) { Text(e.slotAt[i]!!.position.label(vm.lang), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) {
                            Text(p.shortName, fontSize = 11.sp, maxLines = 1)
                            LinearProgressIndicator(progress = { e.energy[i].toFloat() }, Modifier.fillMaxWidth().height(3.dp),
                                color = if (e.energy[i] < 0.6) fm.bad else fm.good, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        Text("%.1f".format(m?.rating ?: 6.5), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp),
                            color = if ((m?.rating ?: 6.5) >= 7) fm.good else if ((m?.rating ?: 6.5) < 6) fm.bad else MaterialTheme.colorScheme.onSurface)
                        if ((m?.yellow ?: 0) > 0) Text(" ▮", color = Color(0xFFFFD600), fontSize = 10.sp)
                    }
                }
            }
            if (user >= 0) OutlinedButton(onClick = { showSubs = true; subOut = -1 }, Modifier.fillMaxWidth()) { Text(vm.t("Ganti (", "Subs (") + "${e.subsRemaining(user)})") }
        }
        // Centre: scoreboard + pitch + controls
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().background(fm.header).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(home.clubName, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Column(Modifier.padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${e.goals[0]} - ${e.goals[1]}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    SmallText(if (e.phase == MatchEngine2D.Phase.FINISHED) vm.t("Selesai", "Full time") else e.clockLabel)
                }
                Text(away.clubName, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxHeight().aspectRatio(105f / 68f)) {
                    @Suppress("UNUSED_EXPRESSION") frame
                    drawPitchH()
                    val sx = size.width / 105f; val sy = size.height / 68f
                    fun pt(x: Double, y: Double) = Offset((x * sx).toFloat(), (y * sy).toFloat())
                    val r = size.height / 68f * 1.25f
                    for (i in 0 until 22) {
                        if (!e.active[i]) continue
                        val t = e.teamOf(i)
                        val col = Color(if (t == 0) home.color else away.color)
                        val c2 = Color(if (t == 0) home.altColor else away.altColor)
                        val isGk = e.slotAt[i]?.position == Position.GK
                        val center = pt(e.px[i], e.py[i])
                        drawCircle(Color.Black.copy(alpha = 0.35f), r + 1f, center + Offset(1.5f, 1.5f))
                        drawCircle(if (isGk) Color(0xFFFFEB3B) else col, r, center)
                        drawCircle(if (c2 == col) Color.White else c2, r, center, style = Stroke(2f))
                        if (i == e.owner) drawCircle(Color.White, r + 4f, center, style = Stroke(1.5f))
                    }
                    val b = pt(e.ballX, e.ballY)
                    val lift = (e.ballZ * 3).toFloat()
                    drawCircle(Color.Black.copy(alpha = 0.4f), r * 0.5f, b + Offset(lift * 0.7f, lift * 0.7f))
                    drawCircle(Color.White, r * 0.5f + lift * 0.15f, b)
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.phase == MatchEngine2D.Phase.FINISHED) {
                    Button(onClick = { vm.finishLiveMatch() }, Modifier.weight(1f)) { Text(vm.t("Laporan Pertandingan", "Match Report")) }
                } else {
                    // Compact segmented controls (no wrapping on narrow phones)
                    Text(if (paused) "▶" else "❚❚", Modifier.clip(RoundedCornerShape(6.dp)).background(fm.header).clickable { paused = !paused }.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    for ((i, label) in listOf("½x", "1x", "2x", "4x", "8x").withIndex()) {
                        Text(label, Modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(6.dp)).background(if (speed == i) MaterialTheme.colorScheme.primary else fm.header)
                            .clickable { speed = i }.padding(horizontal = 8.dp, vertical = 6.dp), fontSize = 12.sp,
                            color = if (speed == i) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(vm.t("Selesaikan", "Finish"), Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.finishLiveMatch() }.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        // Right: commentary / stats / assistant / opposition
        Column(Modifier.width(if (compact) 220.dp else 270.dp).fillMaxHeight().background(fm.panel)) {
            val tabs = listOf(vm.t("Komentar", "Comms"), vm.t("Statistik", "Stats"), vm.t("Asisten", "Assistant"), vm.t("Taktik", "Tactics"))
            Row { for ((i, t) in tabs.withIndex()) Text(t, Modifier.weight(1f).clickable { tab = i }.background(if (tab == i) fm.header else Color.Transparent).padding(vertical = 8.dp),
                textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
            when (tab) {
                0 -> LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                    items(e.events.filter { it.type !in setOf(EvType.FOUL) }.takeLast(60).reversed()) { ev ->
                        val txt = commentary(vm, w, e, ev) ?: return@items
                        val bold = ev.type == EvType.GOAL || ev.type == EvType.PEN_GOAL || ev.type == EvType.RED
                        Text("${ev.minute}'  $txt", fontSize = 11.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                1 -> StatsPanel(vm, e)
                3 -> if (user >= 0) Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                    Text(vm.t("Mentalitas", "Mentality"), style = MaterialTheme.typography.labelLarge)
                    for (m in Mentality.entries) Row(Modifier.fillMaxWidth().clickable { e.setInstructions(user, e.sheets[user].instructions.copy(mentality = m)) },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(e.sheets[user].instructions.mentality == m, { e.setInstructions(user, e.sheets[user].instructions.copy(mentality = m)) }, Modifier.size(32.dp))
                        Text(m.label(vm.lang), fontSize = 12.sp)
                    }
                    LabeledSlider(vm.t("Pressing", "Pressing"), e.sheets[user].instructions.pressing, vm.t("Rendah", "Low"), vm.t("Tinggi", "High")) {
                        e.sheets[user].instructions = e.sheets[user].instructions.copy(pressing = it)
                    }
                    LabeledSlider(vm.t("Garis pertahanan", "Defensive line"), e.sheets[user].instructions.defensiveLine, vm.t("Dalam", "Deep"), vm.t("Tinggi", "High")) {
                        e.sheets[user].instructions = e.sheets[user].instructions.copy(defensiveLine = it)
                    }
                    LabeledSlider(vm.t("Tempo", "Tempo"), e.sheets[user].instructions.tempo, vm.t("Lambat", "Slow"), vm.t("Cepat", "Fast")) {
                        e.sheets[user].instructions = e.sheets[user].instructions.copy(tempo = it)
                    }
                }
                else -> LazyColumn(Modifier.padding(8.dp)) {
                    val adv = Assistant.live(e)
                    if (adv.isEmpty()) item { SmallText(vm.t("Asisten belum punya saran.", "No advice from the assistant yet.")) }
                    items(adv) { (k, args) -> Text("• " + I18n.t(vm.lang, k, args), fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                }
            }
        }
    }
    if (showSubs && user >= 0) SubsDialog(vm, e, user, subOut, { subOut = it }) { showSubs = false }
    if (showHalfTalk) {
        AlertDialog(onDismissRequest = {}, title = { Text(vm.t("Turun Minum — ", "Half-Time — ") + "${e.goals[0]} - ${e.goals[1]}") },
            text = {
                Column(Modifier.widthIn(min = 460.dp)) {
                    SmallText(vm.t("Saran asisten: ", "Assistant suggests: ") + md.suggestedTone(TeamTalk.Stage.HALF).label(vm.lang), MaterialTheme.colorScheme.secondary)
                    if (vm.talkResult.isEmpty()) {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), Modifier.heightIn(max = 220.dp)) {
                            items(TeamTalk.Tone.entries.size) { idx -> val t = TeamTalk.Tone.entries[idx]
                                OutlinedButton(onClick = { vm.teamTalk(TeamTalk.Stage.HALF, t) }, Modifier.padding(2.dp)) { Text(t.label(vm.lang), fontSize = 12.sp) } }
                        }
                    } else {
                        val good = vm.talkResult.count { it.value > 0.15 }; val bad = vm.talkResult.count { it.value < -0.15 }
                        Text(vm.t("Reaksi: ", "Reaction: ") + "$good " + vm.t("termotivasi", "motivated") + ", $bad " + vm.t("negatif", "negative"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHalfTalk = false; vm.talkResult = emptyList() }) { Text(vm.t("Babak kedua", "Second half")) } })
    }
}

private fun DrawScope.drawPitchH() {
    val stripes = 12
    for (i in 0 until stripes) drawRect(if (i % 2 == 0) Pal.pitch else Pal.pitchStripe, Offset(size.width * i / stripes, 0f), Size(size.width / stripes, size.height))
    val l = Color.White.copy(alpha = 0.75f)
    val sx = size.width / 105f; val sy = size.height / 68f
    drawRect(l, style = Stroke(2f))
    drawLine(l, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
    drawCircle(l, 9.15f * sy, Offset(size.width / 2, size.height / 2), style = Stroke(2f))
    for (left in listOf(true, false)) {
        val x0 = if (left) 0f else size.width - 16.5f * sx
        drawRect(l, Offset(x0, (34 - 20.16f) * sy), Size(16.5f * sx, 40.32f * sy), style = Stroke(2f))
        val x1 = if (left) 0f else size.width - 5.5f * sx
        drawRect(l, Offset(x1, (34 - 9.16f) * sy), Size(5.5f * sx, 18.32f * sy), style = Stroke(2f))
        val gx = if (left) -1.5f * sx else size.width
        drawRect(Color.White, Offset(gx, (34 - 3.66f) * sy), Size(1.5f * sx, 7.32f * sy))
        drawCircle(l, 3f, Offset(if (left) 11 * sx else size.width - 11 * sx, size.height / 2))
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
        EvType.GOAL -> I18n.t(l, "ev_goal", name(ev.playerId), team(ev.team)) + (if (ev.otherId >= 0) " (assist ${name(ev.otherId)})" else "")
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
        vm.t("Penguasaan", "Possession") to ((100 * s[0].possessionTicks / totalPoss).toDouble() to (100 * s[1].possessionTicks / totalPoss).toDouble()),
        vm.t("Tembakan", "Shots") to (s[0].shots.toDouble() to s[1].shots.toDouble()),
        vm.t("Tepat sasaran", "On target") to (s[0].onTarget.toDouble() to s[1].onTarget.toDouble()),
        "xG" to (s[0].xg to s[1].xg),
        vm.t("Umpan sukses", "Passes completed") to (s[0].passesCompleted.toDouble() to s[1].passesCompleted.toDouble()),
        vm.t("Tekel", "Tackles") to (s[0].tackles.toDouble() to s[1].tackles.toDouble()),
        vm.t("Pelanggaran", "Fouls") to (s[0].fouls.toDouble() to s[1].fouls.toDouble()),
        vm.t("Kartu kuning", "Yellow cards") to (s[0].yellows.toDouble() to s[1].yellows.toDouble()),
        vm.t("Sepak pojok", "Corners") to (s[0].corners.toDouble() to s[1].corners.toDouble()),
        vm.t("Offside", "Offsides") to (s[0].offsides.toDouble() to s[1].offsides.toDouble()),
    )
    StatBars(rows, Color(e.sheets[0].color), Color(e.sheets[1].color))
}

@Composable
fun StatBars(rows: List<Pair<String, Pair<Double, Double>>>, c1: Color, c2: Color) {
    LazyColumn(Modifier.padding(8.dp)) {
        items(rows) { (k, v) ->
            val total = (v.first + v.second).takeIf { it > 0 } ?: 1.0
            fun fmt(d: Double) = if (d % 1.0 == 0.0) "${d.toInt()}" else "%.2f".format(d)
            Column(Modifier.padding(vertical = 3.dp)) {
                Row { Text(fmt(v.first), fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(k, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp); Text(fmt(v.second), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
                    Box(Modifier.weight((v.first / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(if (c1 == Color.White) Color.LightGray else c1))
                    Box(Modifier.weight((v.second / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(if (c2 == c1) Color.Gray else c2))
                }
            }
        }
    }
}

@Composable
private fun SubsDialog(vm: GameViewModel, e: MatchEngine2D, team: Int, out: Int, onOut: (Int) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClick = onClose) { Text(vm.t("Tutup", "Close")) } },
        title = { Text(vm.t("Pergantian Pemain", "Substitutions") + " (${e.subsRemaining(team)})") },
        text = {
            Row(Modifier.widthIn(min = 480.dp).heightIn(max = 300.dp)) {
                LazyColumn(Modifier.weight(1f)) {
                    item { SmallText(vm.t("Keluar", "Off"), bold = true) }
                    items((team * 11 until team * 11 + 11).filter { e.active[it] }) { i ->
                        val p = e.playerAt[i]!!
                        Text("${e.slotAt[i]!!.position.label(vm.lang)} ${p.shortName} ${(e.energy[i] * 100).toInt()}%",
                            Modifier.fillMaxWidth().clickable { onOut(i) }.background(if (out == i) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent).padding(5.dp), fontSize = 12.sp)
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    item { SmallText(vm.t("Masuk", "On"), bold = true) }
                    items(e.sheets[team].bench.filter { !e.pm.containsKey(it.id) }) { p ->
                        Text("${p.position.label(vm.lang)} ${p.shortName}", Modifier.fillMaxWidth().clickable(enabled = out >= 0) {
                            if (e.substitute(team, out, p.id)) { vm.toast = vm.t("Pergantian dijadwalkan", "Substitution queued"); onOut(-1) }
                        }.padding(5.dp), fontSize = 12.sp, color = if (out >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        })
}

// ---------------------------------------------------------------- report

@Composable
private fun ReportScreen(vm: GameViewModel) {
    val md = vm.matchDay ?: return
    val w = vm.world ?: return
    val rep = md.report ?: return
    val r = rep.result
    Row(Modifier.fillMaxSize().padding(4.dp)) {
        Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) {
            Panel(vm.t("Hasil Akhir", "Final Score")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rep.homeName, Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                    Text("  ${r.homeGoals} - ${r.awayGoals}  ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(rep.awayName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
                if (r.penHome >= 0) Text(vm.t("Adu penalti ", "Penalties ") + "${r.penHome}-${r.penAway}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                for (ev in r.events.filter { it.type == EvType.GOAL || it.type == EvType.PEN_GOAL || it.type == EvType.RED }) {
                    val name = w.players[ev.playerId]?.name ?: "?"
                    Text("${ev.minute}'  " + (if (ev.type == EvType.RED) "🟥 " else "⚽ ") + name, fontSize = 12.sp,
                        textAlign = if (ev.team == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
                w.players[rep.motmId]?.let { Spacer(Modifier.height(6.dp)); Text("⭐ " + vm.t("Pemain terbaik: ", "Player of the match: ") + it.name, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
            }
            Panel(vm.t("Statistik", "Statistics")) {
                val s = r.stats
                val tot = (s[0].possessionTicks + s[1].possessionTicks).coerceAtLeast(1)
                Box(Modifier.height(300.dp)) {
                    StatBars(listOf(
                        vm.t("Penguasaan", "Possession") to ((100 * s[0].possessionTicks / tot).toDouble() to (100 * s[1].possessionTicks / tot).toDouble()),
                        vm.t("Tembakan", "Shots") to (s[0].shots.toDouble() to s[1].shots.toDouble()),
                        vm.t("Tepat sasaran", "On target") to (s[0].onTarget.toDouble() to s[1].onTarget.toDouble()),
                        "xG" to (s[0].xg to s[1].xg),
                        vm.t("Umpan sukses", "Passes completed") to (s[0].passesCompleted.toDouble() to s[1].passesCompleted.toDouble()),
                        vm.t("Pelanggaran", "Fouls") to (s[0].fouls.toDouble() to s[1].fouls.toDouble()),
                        vm.t("Sepak pojok", "Corners") to (s[0].corners.toDouble() to s[1].corners.toDouble()),
                    ), Color(md.engine?.sheets?.get(0)?.color ?: 0xFF4CC38A), Color(md.engine?.sheets?.get(1)?.color ?: 0xFF52A8FF))
                }
            }
        }
        for (t in 0..1) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel((if (t == 0) rep.homeName else rep.awayName) + vm.t(" — Rating", " — Ratings")) {
                for (l in rep.lines[t]) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Box(Modifier.width(36.dp)) { SmallText(l.position?.label(vm.lang) ?: vm.t("CAD", "SUB"), bold = true) }
                    Box(Modifier.weight(1f)) { SmallText(l.player.shortName + (if (l.goals > 0) " ⚽".repeat(l.goals) else "") + (if (l.red) " 🟥" else if (l.yellow > 0) " 🟨" else "")) }
                    SmallText("${l.minutes}'  ", MaterialTheme.colorScheme.onSurfaceVariant)
                    SmallText("%.1f".format(l.rating), if (l.rating >= 7) LocalFm.current.good else if (l.rating < 6) LocalFm.current.bad else MaterialTheme.colorScheme.onSurface, bold = true)
                }
            }
            if (t == 1) Button(onClick = { vm.talkResult = emptyList(); vm.mdStage = MdStage.TALK_FULL }, Modifier.fillMaxWidth().padding(6.dp)) {
                Text(vm.t("Team Talk Akhir Laga", "Full-Time Team Talk"))
            }
        }
    }
}
