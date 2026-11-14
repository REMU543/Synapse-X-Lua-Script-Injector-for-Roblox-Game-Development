package fm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.app.GameViewModel
import fm.app.Screen
import fm.core.*
import fm.engine.ability.RoleFit
import fm.engine.development.Development
import fm.engine.match.Lineups

// ---------------------------------------------------------------- tactics

private fun roleShort(r: Role, lang: Lang): String = r.label(lang).split(" ").filter { it.isNotEmpty() }.let { w ->
    if (w.size == 1) w[0].take(4) else w.take(3).joinToString("") { it.take(1) }.uppercase()
}

@Composable
fun TacticsScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val club = w.userClub ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    var tactic by remember(vm.version) { mutableStateOf(club.tactic) }
    val slots = tactic.effectiveSlots()
    val sheet = remember(tactic, vm.version) { Lineups.pick(w, club, tactic) }
    var pickSlot by remember { mutableIntStateOf(-1) }
    var tab by remember { mutableIntStateOf(0) }
    val dirty = tactic != club.tactic
    fun playerIn(i: Int): Player? = sheet.starters.firstOrNull { it.second == slots[i] }?.first
    Row(Modifier.fillMaxSize()) {
        // Left: formation + pitch
        Column(Modifier.weight(0.44f).fillMaxHeight()) {
            Row(Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(vm.t("Keakraban: ", "Familiarity: ") + "${w.userTacticFamiliarity}%", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (dirty) Button(onClick = { val t = tactic; vm.act { it.setTactic(t) }; vm.toast = vm.t("Taktik disimpan.", "Tactic saved.") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) { Text(vm.t("Simpan", "Save"), fontSize = 12.sp) }
            }
            ChipRow(Formation.ALL.map { it.name }, Formation.ALL.indexOfFirst { it.name == tactic.formation.name }) {
                tactic = tactic.copy(formation = Formation.ALL[it], lineup = emptyList(), slotRoles = emptyList(), slotDuties = emptyList(), slotInstructions = emptyList())
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.aspectRatio(0.72f, matchHeightConstraintsFirst = maxWidth / maxHeight > 0.72f)) {
                    val wPx = maxWidth; val hPx = maxHeight
                    Canvas(Modifier.fillMaxSize()) {
                        val stripes = 8
                        for (k in 0 until stripes) drawRect(if (k % 2 == 0) Pal.pitch else Pal.pitchStripe, Offset(0f, size.height * k / stripes), Size(size.width, size.height / stripes))
                        val line = Color.White.copy(alpha = 0.6f)
                        drawRect(line, style = Stroke(2f))
                        drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
                        drawCircle(line, size.width * 0.13f, Offset(size.width / 2, size.height / 2), style = Stroke(2f))
                        drawRect(line, Offset(size.width * 0.2f, size.height * 0.85f), Size(size.width * 0.6f, size.height * 0.15f), style = Stroke(2f))
                        drawRect(line, Offset(size.width * 0.2f, 0f), Size(size.width * 0.6f, size.height * 0.15f), style = Stroke(2f))
                    }
                    slots.forEachIndexed { i, slot ->
                        val p = playerIn(i)
                        val x = wPx * slot.fy.toFloat() - 36.dp
                        val y = hPx * (0.96f - slot.fx.toFloat() * 1.12f).coerceIn(0.02f, 0.9f) - 4.dp
                        val fitStars = p?.let { RoleFit.stars(it, slot.role, slot.position) } ?: 0.0
                        val ringColor = when { fitStars >= 4 -> LocalFm.current.good; fitStars >= 2.5 -> LocalFm.current.warn; else -> LocalFm.current.bad }
                        Column(Modifier.offset(x, y).width(72.dp).clickable { pickSlot = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(club.primaryColor)).border(2.dp, ringColor, CircleShape), contentAlignment = Alignment.Center) {
                                Text(slot.duty.short, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text(p?.lastName ?: "-", fontSize = 10.sp, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text(roleShort(slot.role, vm.lang) + "-" + slot.duty.short + (if (slot.instructions.count > 0) " •${slot.instructions.count}" else ""),
                                fontSize = 8.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                }
            }
        }
        // Right: tabs with instructions, set pieces and the role overview
        Column(Modifier.weight(0.56f).fillMaxHeight()) {
            ChipRow(listOf(vm.t("Instruksi Tim", "Team Instructions"), vm.t("Peran Pemain", "Player Roles"), vm.t("Bola Mati", "Set Pieces")), tab) { tab = it }
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Panel(vm.t("Dalam Penguasaan Bola", "In Possession")) {
                            Text(vm.t("Mentalitas", "Mentality"), style = MaterialTheme.typography.labelLarge)
                            ChipRow(Mentality.entries.map { it.label(vm.lang) }, tactic.instructions.mentality.ordinal) { tactic = tactic.copy(instructions = tactic.instructions.copy(mentality = Mentality.entries[it])) }
                            val ins = tactic.instructions
                            fun upd(f: (TeamInstructions) -> TeamInstructions) { tactic = tactic.copy(instructions = f(tactic.instructions)) }
                            LabeledSlider(vm.t("Gaya umpan", "Passing directness"), ins.directness, vm.t("Pendek", "Short"), vm.t("Langsung", "Direct")) { v -> upd { it.copy(directness = v) } }
                            LabeledSlider(vm.t("Tempo", "Tempo"), ins.tempo, vm.t("Lambat", "Slow"), vm.t("Cepat", "Fast")) { v -> upd { it.copy(tempo = v) } }
                            LabeledSlider(vm.t("Lebar serangan", "Attacking width"), ins.width, vm.t("Sempit", "Narrow"), vm.t("Lebar", "Wide")) { v -> upd { it.copy(width = v) } }
                            LabeledSlider(vm.t("Umpan silang", "Crossing"), ins.crossing, vm.t("Jarang", "Rarely"), vm.t("Sering", "Often")) { v -> upd { it.copy(crossing = v) } }
                            LabeledSlider(vm.t("Tembakan", "Shooting"), ins.shootingRange, vm.t("Kerja ke kotak", "Work into box"), vm.t("Tembak saat ada peluang", "Shoot on sight")) { v -> upd { it.copy(shootingRange = v) } }
                        }
                    }
                    item {
                        Panel(vm.t("Transisi", "In Transition")) {
                            val ins = tactic.instructions
                            fun upd(f: (TeamInstructions) -> TeamInstructions) { tactic = tactic.copy(instructions = f(tactic.instructions)) }
                            LabeledSlider(vm.t("Setelah kehilangan bola", "On losing the ball"), ins.counterPress, vm.t("Kembali ke posisi", "Regroup"), vm.t("Counter-press", "Counter-press")) { v -> upd { it.copy(counterPress = v) } }
                            LabeledSlider(vm.t("Setelah merebut bola", "On winning the ball"), ins.counterAttack, vm.t("Jaga bentuk", "Hold shape"), vm.t("Serangan balik", "Counter")) { v -> upd { it.copy(counterAttack = v) } }
                            LabeledSlider(vm.t("Distribusi kiper", "Goalkeeper distribution"), ins.gkDistribution, vm.t("Pendek", "Short"), vm.t("Panjang", "Long")) { v -> upd { it.copy(gkDistribution = v) } }
                        }
                    }
                    item {
                        Panel(vm.t("Tanpa Bola", "Out of Possession")) {
                            val ins = tactic.instructions
                            fun upd(f: (TeamInstructions) -> TeamInstructions) { tactic = tactic.copy(instructions = f(tactic.instructions)) }
                            LabeledSlider(vm.t("Garis pertahanan", "Defensive line"), ins.defensiveLine, vm.t("Dalam", "Deep"), vm.t("Tinggi", "High")) { v -> upd { it.copy(defensiveLine = v) } }
                            LabeledSlider(vm.t("Garis tekanan", "Line of engagement"), ins.engagement, vm.t("Rendah", "Low"), vm.t("Tinggi", "High")) { v -> upd { it.copy(engagement = v) } }
                            LabeledSlider(vm.t("Intensitas pressing", "Pressing intensity"), ins.pressing, vm.t("Rendah", "Low"), vm.t("Tinggi", "High")) { v -> upd { it.copy(pressing = v) } }
                            LabeledSlider(vm.t("Lebar bertahan", "Defensive width"), ins.defensiveWidth, vm.t("Sempit", "Narrow"), vm.t("Lebar", "Wide")) { v -> upd { it.copy(defensiveWidth = v) } }
                            LabeledSlider(vm.t("Penjagaan", "Marking"), ins.tightMarking, vm.t("Zona", "Zonal"), vm.t("Ketat", "Tight")) { v -> upd { it.copy(tightMarking = v) } }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.t("Jebakan offside", "Offside trap"), Modifier.weight(1f)); Switch(ins.offsideTrap, { v -> upd { it.copy(offsideTrap = v) } })
                            }
                        }
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    items(slots.indices.toList()) { i ->
                        val slot = slots[i]; val p = playerIn(i)
                        Row(Modifier.fillMaxWidth().clickable { pickSlot = i }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(38.dp)) { SmallText(slot.position.label(vm.lang), bold = true) }
                            Column(Modifier.weight(1f)) {
                                SmallText(p?.name ?: "-", bold = true)
                                SmallText(slot.role.label(vm.lang) + " — " + slot.duty.label(vm.lang), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (p != null) Stars(RoleFit.stars(p, slot.role, slot.position), 10.dp)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Panel(vm.t("Eksekutor & Kapten", "Takers & Captain")) {
                            val xi = sheet.starters.map { it.first }
                            TakerRow(vm, vm.t("Kapten", "Captain"), xi, tactic.captainId) { tactic = tactic.copy(captainId = it) }
                            TakerRow(vm, vm.t("Penendang penalti", "Penalty taker"), xi, tactic.penaltyTakerId, Attr.PENALTY) { tactic = tactic.copy(penaltyTakerId = it) }
                            TakerRow(vm, vm.t("Tendangan bebas", "Free kicks"), xi, tactic.freeKickTakerId, Attr.FREE_KICK) { tactic = tactic.copy(freeKickTakerId = it) }
                            TakerRow(vm, vm.t("Tendangan sudut", "Corners"), xi, tactic.cornerTakerId, Attr.CORNERS) { tactic = tactic.copy(cornerTakerId = it) }
                        }
                    }
                    item {
                        Panel(vm.t("Rutinitas Sepak Pojok", "Corner Routine")) {
                            for (r in CornerRoutine.entries) Row(Modifier.fillMaxWidth().clickable { tactic = tactic.copy(cornerRoutine = r) }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(tactic.cornerRoutine == r, { tactic = tactic.copy(cornerRoutine = r) }); Text(r.label(vm.lang))
                            }
                        }
                    }
                }
            }
        }
    }
    if (pickSlot >= 0) SlotDialog(vm, w, club, tactic, slots, pickSlot, sheet.starters.map { it.first.id }, { tactic = it }) { pickSlot = -1 }
}

@Composable
private fun TakerRow(vm: GameViewModel, label: String, xi: List<Player>, current: Long, attr: Attr? = null, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Box {
            TextButton(onClick = { open = true }) { Text(xi.firstOrNull { it.id == current }?.name ?: vm.t("Otomatis", "Automatic"), fontSize = 12.sp) }
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem(text = { Text(vm.t("Otomatis", "Automatic")) }, onClick = { onPick(-1); open = false })
                for (p in (if (attr != null) xi.sortedByDescending { it.attr(attr) } else xi.sortedByDescending { it.attr(Attr.LEADERSHIP) }))
                    DropdownMenuItem(text = { Text(p.name + (attr?.let { " (${p.attrInt(it)})" } ?: " (${p.attrInt(Attr.LEADERSHIP)})")) }, onClick = { onPick(p.id); open = false })
            }
        }
    }
}

/** Per-slot dialog: choose the player, the role & duty, and individual instructions. */
@Composable
private fun SlotDialog(vm: GameViewModel, w: World, club: Club, tactic: Tactic, slots: List<Slot>, i: Int, xiIds: List<Long>,
                       onChange: (Tactic) -> Unit, onClose: () -> Unit) {
    val slot = slots[i]
    var tab by remember { mutableIntStateOf(0) }
    val current = w.players[xiIds.getOrNull(i) ?: -1]
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        title = { Text("${slot.position.label(vm.lang)} — ${slot.role.label(vm.lang)} (${slot.duty.label(vm.lang)})", fontSize = 16.sp) },
        text = {
            Column(Modifier.widthIn(min = 520.dp).heightIn(max = 330.dp)) {
                ChipRow(listOf(vm.t("Pemain", "Player"), vm.t("Peran & Tugas", "Role & Duty"), vm.t("Instruksi", "Instructions")), tab) { tab = it }
                when (tab) {
                    0 -> LazyColumn {
                        val options = w.squad(club.id).filter { it.available }.sortedByDescending { RoleFit.rating(it, slot.role, slot.position) }
                        items(options, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth().clickable {
                                val ids = xiIds.toMutableList(); while (ids.size < 11) ids.add(-1)
                                val existing = ids.indexOf(p.id)
                                if (existing >= 0) ids[existing] = ids[i]
                                ids[i] = p.id
                                onChange(tactic.copy(lineup = ids)); onClose()
                            }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(40.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
                                Box(Modifier.weight(1f)) { SmallText(p.name + if (p.id in xiIds) vm.t(" (inti)", " (XI)") else "") }
                                SmallText("${p.condition}%  ", MaterialTheme.colorScheme.onSurfaceVariant)
                                Stars(RoleFit.stars(p, slot.role, slot.position), 10.dp)
                            }
                        }
                    }
                    1 -> LazyColumn {
                        item { SmallText(vm.t("Kecocokan pemain saat ini untuk tiap peran:", "Current player's suitability for each role:"), MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(Role.forPosition(slot.position)) { r ->
                            Row(Modifier.fillMaxWidth().clickable { onChange(tactic.withSlot(i, role = r)) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(slot.role == r, { onChange(tactic.withSlot(i, role = r)) }, Modifier.size(30.dp))
                                Text(r.label(vm.lang), Modifier.weight(1f), fontSize = 13.sp)
                                if (current != null) Stars(RoleFit.stars(current, r, slot.position), 10.dp)
                            }
                        }
                        item {
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.t("Tugas: ", "Duty: "), fontWeight = FontWeight.Bold)
                                for (d in Role.duties(slot.role)) {
                                    FilterChip(slot.duty == d, { onChange(tactic.withSlot(i, duty = d)) }, { Text(d.label(vm.lang)) }); Spacer(Modifier.width(4.dp))
                                }
                            }
                            SmallText(vm.t("Atribut kunci: ", "Key attributes: ") + (RoleFit.keys[slot.role] ?: emptyList()).joinToString { it.label(vm.lang) },
                                MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        val pi = slot.instructions
                        @Composable fun opt(label: String, v: Boolean, f: (Boolean) -> PlayerInstructions) =
                            Row(Modifier.fillMaxWidth().clickable { onChange(tactic.withSlot(i, instr = f(!v))) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(v, { onChange(tactic.withSlot(i, instr = f(it))) }); Text(label, fontSize = 13.sp)
                            }
                        opt(vm.t("Lebih sering menembak", "Shoot more often"), pi.shootMore) { pi.copy(shootMore = it) }
                        opt(vm.t("Lebih sering menggiring", "Dribble more"), pi.dribbleMore) { pi.copy(dribbleMore = it) }
                        opt(vm.t("Lebih sering umpan silang", "Cross more often"), pi.crossMore) { pi.copy(crossMore = it) }
                        opt(vm.t("Tekan lawan lebih agresif", "Close down more"), pi.closeDownMore) { pi.copy(closeDownMore = it) }
                        opt(vm.t("Tetap melebar", "Stay wider"), pi.stayWider) { pi.copy(stayWider = it, holdPosition = pi.holdPosition) }
                        opt(vm.t("Jaga posisi", "Hold position"), pi.holdPosition) { pi.copy(holdPosition = it, getForward = if (it) false else pi.getForward) }
                        opt(vm.t("Lebih sering maju", "Get further forward"), pi.getForward) { pi.copy(getForward = it, holdPosition = if (it) false else pi.holdPosition) }
                        opt(vm.t("Tekel lebih keras", "Tackle harder"), pi.tackleHarder) { pi.copy(tackleHarder = it) }
                    }
                }
            }
        })
}

// ---------------------------------------------------------------- training

@Composable
fun TrainingScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    val club = w.userClub ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    val plan = Development.plan(club)
    var editDay by remember { mutableIntStateOf(-1) }
    var individual by remember { mutableStateOf<Player?>(null) }
    val days = if (vm.lang == Lang.ID) listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min") else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    LazyVerticalGrid(GridCells.Adaptive(360.dp), Modifier.fillMaxSize().padding(6.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Panel(vm.t("Jadwal Latihan Mingguan", "Weekly Training Schedule"), action = {
                val load = Development.load(plan)
                Text(vm.t("Beban: ", "Load: ") + when { load >= 13 -> vm.t("Berat", "Heavy"); load >= 8 -> vm.t("Normal", "Normal"); else -> vm.t("Ringan", "Light") },
                    color = when { load >= 13 -> LocalFm.current.bad; load >= 8 -> LocalFm.current.good; else -> LocalFm.current.warn }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val s = plan[d]
                        Column(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(6.dp)).background(sessionColor(s)).clickable { editDay = d }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(days[d], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(s.label(vm.lang), fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                SmallText(vm.t("Ketuk hari untuk mengubah sesi. Laga di hari itu menggantikan sesi latihan.", "Tap a day to change its session. A match replaces that day's session."),
                    MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 4.dp)) {
                    for (f in TrainingFocus.entries.take(6)) {
                        TextButton(onClick = { vm.act { it.world.userClub?.weeklyPlan = Development.defaultPlan(f).toMutableList() } }) { Text(f.label(vm.lang), fontSize = 11.sp) }
                    }
                }
            }
        }
        item {
            Panel(vm.t("Staf Pelatih & Fasilitas", "Coaching Staff & Facilities")) {
                KeyValue(vm.t("Fasilitas latihan", "Training facilities"), "${club.trainingFacilities}/20")
                KeyValue(vm.t("Kualitas pelatih", "Coaching quality"), "%.1f/20".format(Development.coachQuality(w, club, false)))
                KeyValue(vm.t("Pelatih kiper", "GK coaching"), "%.1f/20".format(Development.coachQuality(w, club, true)))
                KeyValue(vm.t("Keakraban taktik", "Tactical familiarity"), "${w.userTacticFamiliarity}%")
                Text(vm.t("Intensitas", "Intensity"), style = MaterialTheme.typography.labelLarge)
                ChipRow(listOf(vm.t("Ringan", "Light"), vm.t("Normal", "Normal"), vm.t("Berat", "Heavy")), club.trainingIntensity - 1) { i -> vm.act { it.setTraining(club.trainingFocus, i + 1) } }
            }
        }
        item {
            Panel(vm.t("Latihan Individu", "Individual Training")) {
                for (p in w.squad(club.id).sortedBy { it.position.ordinal }) {
                    Row(Modifier.fillMaxWidth().clickable { individual = p }.padding(vertical = 3.dp)) {
                        Box(Modifier.width(36.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
                        Box(Modifier.weight(1f)) { SmallText(p.name) }
                        val focus = listOfNotNull(p.individualAttr?.label(vm.lang), p.retrainPosition?.let { vm.t("Posisi baru: ", "New position: ") + it.label(vm.lang) + " (${p.retrainWeeks}w)" })
                        SmallText(if (focus.isEmpty()) "—" else focus.joinToString(" · "), if (focus.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (editDay >= 0) AlertDialog(onDismissRequest = { editDay = -1 }, confirmButton = { TextButton(onClick = { editDay = -1 }) { Text("OK") } },
        title = { Text(days[editDay]) },
        text = {
            Column {
                for (s in Session.entries) Row(Modifier.fillMaxWidth().clickable {
                    val d = editDay
                    vm.act { cc -> val cl = cc.world.userClub ?: return@act; val pl = Development.plan(cl).toMutableList(); pl[d] = s; cl.weeklyPlan = pl }
                    editDay = -1
                }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(sessionColor(s))); Spacer(Modifier.width(8.dp))
                    Text(s.label(vm.lang), Modifier.weight(1f))
                    SmallText(vm.t("beban ", "load ") + s.load, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        })
    individual?.let { p -> IndividualTrainingDialog(vm, p) { individual = null } }
}

private fun sessionColor(s: Session) = when (s) {
    Session.REST -> Color(0xFF546E7A); Session.RECOVERY -> Color(0xFF26A69A); Session.FITNESS -> Color(0xFFE53935)
    Session.TECHNICAL -> Color(0xFF1E88E5); Session.TACTICAL -> Color(0xFF8E24AA); Session.ATTACKING -> Color(0xFFF4511E)
    Session.DEFENDING -> Color(0xFF3949AB); Session.SET_PIECES -> Color(0xFF6D4C41); Session.MATCH_PREP -> Color(0xFF43A047)
}

@Composable
private fun IndividualTrainingDialog(vm: GameViewModel, p: Player, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        title = { Text(vm.t("Latihan individu — ", "Individual training — ") + p.name, fontSize = 16.sp) },
        text = {
            Column(Modifier.widthIn(min = 480.dp).heightIn(max = 320.dp)) {
                ChipRow(listOf(vm.t("Fokus atribut", "Attribute focus"), vm.t("Latih posisi baru", "Learn new position")), tab) { tab = it }
                if (tab == 0) LazyColumn {
                    item {
                        Row(Modifier.fillMaxWidth().clickable { vm.act { p.individualAttr = null }; onClose() }.padding(6.dp)) { Text(vm.t("Tanpa fokus", "No focus")) }
                    }
                    val attrs = Attr.entries.filter { (p.position == Position.GK) == (it.group == AttrGroup.GOALKEEPING) || it.group == AttrGroup.MENTAL || it.group == AttrGroup.PHYSICAL }
                    items(attrs) { a ->
                        Row(Modifier.fillMaxWidth().clickable { vm.act { p.individualAttr = a }; onClose() }.padding(vertical = 4.dp, horizontal = 6.dp)) {
                            Text(a.label(vm.lang), Modifier.weight(1f), fontWeight = if (p.individualAttr == a) FontWeight.Bold else FontWeight.Normal)
                            Text("${p.attrInt(a)}", fontWeight = FontWeight.Bold)
                        }
                    }
                } else LazyColumn {
                    item {
                        Row(Modifier.fillMaxWidth().clickable { vm.act { p.retrainPosition = null; p.retrainWeeks = 0 }; onClose() }.padding(6.dp)) { Text(vm.t("Tidak ada", "None")) }
                    }
                    items(Position.entries.filter { it != p.position && it !in p.secondary && it != Position.GK }) { pos ->
                        Row(Modifier.fillMaxWidth().clickable { vm.act { p.retrainPosition = pos; p.retrainWeeks = 0 }; onClose() }.padding(vertical = 4.dp, horizontal = 6.dp)) {
                            Text(pos.label(vm.lang), Modifier.weight(1f), fontWeight = if (p.retrainPosition == pos) FontWeight.Bold else FontWeight.Normal)
                            SmallText(vm.t("Kecocokan ", "Familiarity ") + "${(fm.engine.ability.Ability.familiarity(p, pos) * 100).toInt()}%", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        })
}

// ---------------------------------------------------------------- staff

@Composable
fun StaffScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val club = w.userClub ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    var tab by remember { mutableIntStateOf(0) }
    var roleFilter by remember { mutableIntStateOf(0) }
    var confirmFire by remember { mutableStateOf<Staff?>(null) }
    val roles = listOf<StaffRole?>(null) + Development.hireableRoles
    Column(Modifier.fillMaxSize()) {
        ChipRow(listOf(vm.t("Staf Klub", "Club Staff"), vm.t("Bursa Staf", "Staff Market")), tab) { tab = it }
        if (tab == 1) ChipRow(roles.map { it?.label(vm.lang) ?: vm.t("Semua", "All") }, roleFilter) { roleFilter = it }
        val list = if (tab == 0) club.staffIds.mapNotNull { w.staff[it] }.sortedBy { it.role.ordinal } else c.staffMarket(roles[roleFilter])
        val cols = listOf<Col<Staff>>(
            Col(vm.t("Peran", "Role"), 120.dp, { it.role.ordinal }) { SmallText(it.role.label(vm.lang), bold = true) },
            Col(vm.t("Nama", "Name"), androidx.compose.ui.unit.Dp.Unspecified, { it.lastName }) { SmallText(it.name) },
            Col(vm.t("Usia", "Age"), 38.dp, { it.age(w.today) }) { SmallText("${it.age(w.today)}") },
            Col(vm.t("Melatih", "Coach"), 50.dp, { it.coaching }) { AttrNum(it.coaching) },
            Col(vm.t("Taktik", "Tact"), 46.dp, { it.tactical }) { AttrNum(it.tactical) },
            Col(vm.t("Nilai Kmp", "Judge A"), 56.dp, { it.judgingAbility }) { AttrNum(it.judgingAbility) },
            Col(vm.t("Nilai Pot", "Judge P"), 56.dp, { it.judgingPotential }) { AttrNum(it.judgingPotential) },
            Col(vm.t("Medis", "Med"), 44.dp, { it.medical }) { AttrNum(it.medical) },
            Col(vm.t("Muda", "Youth"), 44.dp, { it.youthWork }) { AttrNum(it.youthWork) },
            Col(vm.t("Gaji", "Wage"), 72.dp, { it.contract?.wage ?: 0L }) { SmallText(Money.format(it.contract?.wage ?: c.staffWageFor(it), vm.lang)) },
            Col("", 80.dp) { s ->
                if (tab == 0) TextButton(onClick = { confirmFire = s }) { Text(vm.t("Pecat", "Dismiss"), fontSize = 11.sp, color = LocalFm.current.bad) }
                else TextButton(onClick = { vm.act { cc -> cc.hireStaff(s.id)?.let { key -> vm.toast = I18n.t(vm.lang, key) } } }) { Text(vm.t("Rekrut", "Hire"), fontSize = 11.sp) }
            },
        )
        SortableTable(list, cols, key = { it.id }, onClick = {})
    }
    confirmFire?.let { s ->
        AlertDialog(onDismissRequest = { confirmFire = null },
            confirmButton = { TextButton(onClick = { vm.act { it.fireStaff(s.id) }; confirmFire = null }) { Text(vm.t("Pecat", "Dismiss")) } },
            dismissButton = { TextButton(onClick = { confirmFire = null }) { Text(vm.t("Batal", "Cancel")) } },
            title = { Text(vm.t("Pecat ", "Dismiss ") + s.name + "?") },
            text = { Text(vm.t("Klub membayar kompensasi sisa kontrak (maks. 12 bulan gaji).", "The club pays compensation for the rest of the contract (max 12 months' wages).")) })
    }
}

@Composable
private fun AttrNum(v: Int) {
    val color = when { v >= 16 -> Color(0xFF2ECC71); v >= 12 -> Color(0xFF9CCC65); v >= 8 -> Color(0xFFFFCA28); else -> Color(0xFFEF6C00) }
    Text("$v", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
}
