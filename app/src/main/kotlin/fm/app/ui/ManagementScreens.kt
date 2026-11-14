package fm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.app.GameViewModel
import fm.app.Screen
import fm.core.*
import fm.engine.market.Transfers
import fm.engine.match.Lineups

// ---------------------------------------------------------------- player profile

@Composable
fun PlayerScreen(vm: GameViewModel, id: Long) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val p = w.players[id] ?: run { Text(vm.t("Pemain sudah pensiun.", "This player has retired."), Modifier.padding(16.dp)); return }
    val club = w.clubs[p.clubId]
    val own = p.clubId == w.manager.clubId && p.clubId >= 0
    var dialog by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClubBadge(club, 40.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${p.position.label(vm.lang)} · ${p.age(w.today)} ${vm.t("th", "y")} · ${w.nation(p.nationId).name(vm.lang)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(club?.name ?: vm.t("Bebas transfer", "Free agent"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val ref = w.userClub?.let { uc -> w.squad(uc.id).map { it.ca }.sortedDescending().take(11).average() }?.takeIf { !it.isNaN() } ?: 100.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallText(vm.t("Kemampuan ", "Ability ")); Stars(if (c.knowledge(p) >= 60) Ability.starsFor(p.ca, ref) else c.potentialStars(p) * 0.8)
                    Spacer(Modifier.width(12.dp)); SmallText(vm.t("Potensi ", "Potential ")); Stars(c.potentialStars(p))
                }
                KeyValue(vm.t("Nilai pasar", "Value"), Money.format(p.value, vm.lang))
                KeyValue(vm.t("Kaki", "Foot"), when (p.foot) { Foot.RIGHT -> vm.t("Kanan", "Right"); Foot.LEFT -> vm.t("Kiri", "Left"); Foot.BOTH -> vm.t("Keduanya", "Both") })
                KeyValue(vm.t("Tinggi / Berat", "Height / Weight"), "${p.heightCm} cm / ${p.weightKg} kg")
                KeyValue(vm.t("Kepribadian", "Personality"), if (c.knowledge(p) >= 70) p.personality.describe(vm.lang) else "?")
                KeyValue(vm.t("Kondisi / Moral", "Condition / Morale"), "${p.condition}% / ${moraleLabel(p.morale)}")
                if (p.injuryDays > 0) KeyValue(vm.t("Cedera", "Injury"), "${p.injuryName} (${p.injuryDays}h)", MaterialTheme.colorScheme.error)
                if (c.knowledge(p) < 100) KeyValue(vm.t("Pengetahuan scout", "Scout knowledge"), "${c.knowledge(p)}%")
            }
        }
        item {
            val groups = if (p.position == Position.GK) listOf(AttrGroup.GOALKEEPING, AttrGroup.MENTAL, AttrGroup.PHYSICAL)
            else listOf(AttrGroup.TECHNICAL, AttrGroup.MENTAL, AttrGroup.PHYSICAL)
            SectionCard(vm.t("Atribut", "Attributes")) {
                Row {
                    for (g in groups) Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        SmallText(when (g) { AttrGroup.TECHNICAL -> vm.t("Teknik", "Technical"); AttrGroup.MENTAL -> vm.t("Mental", "Mental"); AttrGroup.PHYSICAL -> vm.t("Fisik", "Physical"); AttrGroup.GOALKEEPING -> vm.t("Kiper", "Goalkeeping") }, MaterialTheme.colorScheme.primary, true)
                        for (a in Attr.entries.filter { it.group == g }) AttrCell(a.label(vm.lang), c.attrRange(p, a))
                    }
                }
            }
        }
        item {
            SectionCard(vm.t("Kontrak & Musim Ini", "Contract & Season")) {
                p.contract?.let { ct ->
                    KeyValue(vm.t("Gaji / bulan", "Wage / month"), Money.format(ct.wage, vm.lang))
                    KeyValue(vm.t("Berakhir", "Expires"), "30 Jun ${ct.endYear}")
                    KeyValue(vm.t("Status", "Status"), ct.squadStatus.label(vm.lang))
                }
                KeyValue(vm.t("Main / Gol / Assist", "Apps / Goals / Assists"), "${p.season.apps} / ${p.season.goals} / ${p.season.assists}")
                KeyValue(vm.t("Rating rata-rata", "Average rating"), if (p.season.apps > 0) "%.2f".format(p.season.avgRating) else "-")
            }
        }
        if (p.history.isNotEmpty()) item {
            SectionCard(vm.t("Riwayat Karier", "Career History")) {
                for (h in p.history.takeLast(10).reversed()) Row {
                    SmallText("${h.season}/${(h.season + 1) % 100}"); Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) { SmallText(h.clubName) }
                    SmallText("${h.apps} ${vm.t("main", "apps")} · ${h.goals} ${vm.t("gol", "gls")} · ${"%.2f".format(h.avgRating)}")
                }
            }
        }
        item {
            Column(Modifier.padding(12.dp)) {
                if (own) {
                    Button(onClick = { dialog = "renew" }, Modifier.fillMaxWidth()) { Text(vm.t("Tawarkan kontrak baru", "Offer new contract")) }
                    OutlinedButton(onClick = { vm.act { it.toggleTransferList(p.id) } }, Modifier.fillMaxWidth()) {
                        Text(if (p.transferListed) vm.t("Hapus dari daftar jual", "Remove from transfer list") else vm.t("Masukkan daftar jual", "Add to transfer list"))
                    }
                    OutlinedButton(onClick = { dialog = "release" }, Modifier.fillMaxWidth()) { Text(vm.t("Putus kontrak", "Release")) }
                } else if (w.manager.clubId >= 0) {
                    Button(onClick = { dialog = "offer" }, Modifier.fillMaxWidth()) {
                        Text(if (p.clubId < 0) vm.t("Tawarkan kontrak", "Offer contract") else vm.t("Ajukan tawaran transfer", "Make transfer offer"))
                    }
                    OutlinedButton(onClick = { vm.act { cc -> if (p.id !in cc.world.manager.shortlist) cc.world.manager.shortlist.add(p.id) } }, Modifier.fillMaxWidth()) {
                        Text(vm.t("Tambah ke daftar pantauan", "Add to shortlist"))
                    }
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
    when (dialog) {
        "renew" -> MoneyDialog(vm, vm.t("Kontrak baru", "New contract"), Transfers.renewalDemand(w, p), showFee = false, onDismiss = { dialog = null }) { _, wage, years ->
            vm.act { cc -> if (!cc.renew(p.id, wage, years)) vm.toast = vm.t("Pemain menolak tawaran.", "The player rejected the offer.") }
            dialog = null
        }
        "offer" -> {
            val user = w.userClub!!
            MoneyDialog(vm, vm.t("Tawaran untuk ", "Offer for ") + p.name, Transfers.wageDemand(w, p, user), showFee = p.clubId >= 0, fee = p.value, onDismiss = { dialog = null }) { fee, wage, years ->
                vm.act { cc ->
                    val msg = if (p.clubId < 0) {
                        if (cc.signFreeAgent(p.id, wage, years)) vm.t("Pemain bergabung!", "Player signed!") else vm.t("Pemain menolak.", "Player refused.")
                    } else when (val r = cc.offer(p.id, fee, wage, years)) {
                        is Transfers.OfferResult.Accepted -> vm.t("Transfer selesai!", "Transfer completed!")
                        is Transfers.OfferResult.Counter -> vm.t("Klub meminta ", "Club asks for ") + Money.format(r.fee, vm.lang)
                        is Transfers.OfferResult.PlayerRefused -> vm.t("Pemain menolak (", "Player refused (") + r.reasonKey + ")"
                        is Transfers.OfferResult.Rejected -> vm.t("Tawaran ditolak (", "Offer rejected (") + r.reasonKey + ")"
                    }
                    vm.toast = msg
                }
                dialog = null
            }
        }
        "release" -> AlertDialog(onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { vm.act { it.releasePlayer(p.id) }; dialog = null; vm.back() }) { Text(vm.t("Putus", "Release")) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(vm.t("Batal", "Cancel")) } },
            title = { Text(vm.t("Putus kontrak?", "Release player?")) },
            text = { Text(vm.t("Klub membayar kompensasi sisa kontrak.", "The club pays compensation for the remaining contract.")) })
    }
}

@Composable
fun MoneyDialog(vm: GameViewModel, title: String, wageDemand: Long, showFee: Boolean, fee: Long = 0, onDismiss: () -> Unit, onConfirm: (Long, Long, Int) -> Unit) {
    var feeJt by remember { mutableStateOf((fee / Money.JUTA).toString()) }
    var wageJt by remember { mutableStateOf(((wageDemand + Money.JUTA - 1) / Money.JUTA).toString()) }
    var years by remember { mutableIntStateOf(3) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                if (showFee) OutlinedTextField(feeJt, { feeJt = it.filter(Char::isDigit) }, label = { Text(vm.t("Biaya transfer (juta Rp)", "Fee (million Rp)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(wageJt, { wageJt = it.filter(Char::isDigit) }, label = { Text(vm.t("Gaji per bulan (juta Rp)", "Monthly wage (million Rp)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                SmallText(vm.t("Permintaan agen: ", "Agent asks: ") + Money.format(wageDemand, vm.lang), MaterialTheme.colorScheme.onSurfaceVariant)
                Text(vm.t("Durasi: ", "Length: ") + "$years " + vm.t("tahun", "years"))
                Slider(years.toFloat(), { years = it.toInt() }, valueRange = 1f..5f, steps = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm((feeJt.toLongOrNull() ?: 0) * Money.JUTA, (wageJt.toLongOrNull() ?: 0) * Money.JUTA, years) }) { Text(vm.t("Kirim", "Submit")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(vm.t("Batal", "Cancel")) } })
}

// ---------------------------------------------------------------- tactics

@Composable
fun TacticsScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val club = w.userClub ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    var tactic by remember(vm.version) { mutableStateOf(club.tactic) }
    val sheet = remember(tactic, vm.version) { Lineups.pick(w, club, tactic) }
    var pickSlot by remember { mutableIntStateOf(-1) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(vm.t("Formasi", "Formation"), Modifier.padding(12.dp, 8.dp), style = MaterialTheme.typography.titleSmall)
            ChipRow(Formation.ALL.map { it.name }, Formation.ALL.indexOfFirst { it.name == tactic.formation.name }) {
                tactic = tactic.copy(formation = Formation.ALL[it], lineup = emptyList())
            }
            SmallText("  " + vm.t("Keakraban taktik: ", "Tactical familiarity: ") + "${w.userTacticFamiliarity}%", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            // Tactical pitch: tap a slot to choose a player.
            BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp).aspectRatio(0.72f)) {
                val wPx = maxWidth; val hPx = maxHeight
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Pal.pitch)
                    val line = Color.White.copy(alpha = 0.6f)
                    drawRect(line, style = Stroke(3f))
                    drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f)
                    drawCircle(line, size.width * 0.14f, Offset(size.width / 2, size.height / 2), style = Stroke(3f))
                    drawRect(line, Offset(size.width * 0.2f, size.height * 0.84f), androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.16f), style = Stroke(3f))
                    drawRect(line, Offset(size.width * 0.2f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.16f), style = Stroke(3f))
                }
                tactic.formation.slots.forEachIndexed { i, slot ->
                    val p = sheet.starters.getOrNull(i)?.takeIf { it.second == slot }?.first ?: sheet.starters.firstOrNull { it.second == slot }?.first
                    val x = wPx * slot.fy.toFloat() - 34.dp
                    val y = hPx * (1f - slot.fx.toFloat() * 1.05f).coerceIn(0.03f, 0.95f) - 20.dp
                    Column(Modifier.offset(x, y).width(68.dp).clickable { pickSlot = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Color(club.primaryColor)), contentAlignment = Alignment.Center) {
                            Text(slot.position.label(vm.lang), fontSize = 8.sp, color = Color(club.secondaryColor), fontWeight = FontWeight.Bold)
                        }
                        Text(p?.lastName ?: "-", fontSize = 10.sp, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(slot.role.label(vm.lang), fontSize = 8.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                    }
                }
            }
        }
        item {
            SectionCard(vm.t("Instruksi Tim", "Team Instructions")) {
                Text(vm.t("Mentalitas", "Mentality"), style = MaterialTheme.typography.labelLarge)
                ChipRow(Mentality.entries.map { it.label(vm.lang) }, tactic.instructions.mentality.ordinal) { tactic = tactic.copy(instructions = tactic.instructions.copy(mentality = Mentality.entries[it])) }
                val ins = tactic.instructions
                fun upd(f: (TeamInstructions) -> TeamInstructions) { tactic = tactic.copy(instructions = f(tactic.instructions)) }
                LabeledSlider(vm.t("Gaya umpan", "Passing directness"), ins.directness, vm.t("Pendek", "Short"), vm.t("Langsung", "Direct")) { v -> upd { it.copy(directness = v) } }
                LabeledSlider(vm.t("Tempo", "Tempo"), ins.tempo, vm.t("Lambat", "Slow"), vm.t("Cepat", "Fast")) { v -> upd { it.copy(tempo = v) } }
                LabeledSlider(vm.t("Lebar serangan", "Attacking width"), ins.width, vm.t("Sempit", "Narrow"), vm.t("Lebar", "Wide")) { v -> upd { it.copy(width = v) } }
                LabeledSlider(vm.t("Umpan silang", "Crossing"), ins.crossing, vm.t("Jarang", "Rarely"), vm.t("Sering", "Often")) { v -> upd { it.copy(crossing = v) } }
                LabeledSlider(vm.t("Tembakan jarak jauh", "Shooting range"), ins.shootingRange, vm.t("Kerja ke kotak", "Work into box"), vm.t("Tembak saat ada peluang", "Shoot on sight")) { v -> upd { it.copy(shootingRange = v) } }
                LabeledSlider(vm.t("Garis pertahanan", "Defensive line"), ins.defensiveLine, vm.t("Dalam", "Deep"), vm.t("Tinggi", "High")) { v -> upd { it.copy(defensiveLine = v) } }
                LabeledSlider(vm.t("Garis tekanan", "Line of engagement"), ins.engagement, vm.t("Rendah", "Low"), vm.t("Tinggi", "High")) { v -> upd { it.copy(engagement = v) } }
                LabeledSlider(vm.t("Intensitas pressing", "Pressing intensity"), ins.pressing, vm.t("Rendah", "Low"), vm.t("Tinggi", "High")) { v -> upd { it.copy(pressing = v) } }
                LabeledSlider(vm.t("Lebar bertahan", "Defensive width"), ins.defensiveWidth, vm.t("Sempit", "Narrow"), vm.t("Lebar", "Wide")) { v -> upd { it.copy(defensiveWidth = v) } }
                LabeledSlider(vm.t("Penjagaan", "Marking"), ins.tightMarking, vm.t("Zona", "Zonal"), vm.t("Ketat", "Tight")) { v -> upd { it.copy(tightMarking = v) } }
                LabeledSlider(vm.t("Setelah kehilangan bola", "On losing the ball"), ins.counterPress, vm.t("Kembali ke posisi", "Regroup"), vm.t("Counter-press", "Counter-press")) { v -> upd { it.copy(counterPress = v) } }
                LabeledSlider(vm.t("Setelah merebut bola", "On winning the ball"), ins.counterAttack, vm.t("Jaga bentuk", "Hold shape"), vm.t("Serangan balik", "Counter")) { v -> upd { it.copy(counterAttack = v) } }
                LabeledSlider(vm.t("Distribusi kiper", "Goalkeeper distribution"), ins.gkDistribution, vm.t("Pendek", "Short"), vm.t("Panjang", "Long")) { v -> upd { it.copy(gkDistribution = v) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.t("Jebakan offside", "Offside trap"), Modifier.weight(1f)); Switch(ins.offsideTrap, { v -> upd { it.copy(offsideTrap = v) } })
                }
                Button(onClick = { val t = tactic; vm.act { it.setTactic(t) }; vm.toast = vm.t("Taktik disimpan.", "Tactic saved.") }, Modifier.fillMaxWidth()) { Text(vm.t("Simpan Taktik", "Save Tactic")) }
                OutlinedButton(onClick = { tactic = tactic.copy(lineup = emptyList()) }, Modifier.fillMaxWidth()) { Text(vm.t("Pilih susunan otomatis", "Auto-pick lineup")) }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
    if (pickSlot >= 0) {
        val slot = tactic.formation.slots[pickSlot]
        val options = w.squad(club.id).filter { it.available }.sortedByDescending { fm.engine.ability.Ability.effective(it, slot.position) }
        AlertDialog(onDismissRequest = { pickSlot = -1 }, confirmButton = { TextButton(onClick = { pickSlot = -1 }) { Text("OK") } },
            title = { Text("${slot.position.label(vm.lang)} — ${slot.role.label(vm.lang)}") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().clickable {
                            val current = sheet.starters.map { it.first.id }.toMutableList()
                            while (current.size < 11) current.add(-1)
                            val existing = current.indexOf(p.id)
                            if (existing >= 0) current[existing] = current[pickSlot]
                            current[pickSlot] = p.id
                            tactic = tactic.copy(lineup = current)
                            pickSlot = -1
                        }.padding(vertical = 8.dp)) {
                            Text("${p.position.label(vm.lang)}  ${p.name}", Modifier.weight(1f))
                            Text("%.0f".format(fm.engine.ability.Ability.effective(p, slot.position)))
                        }
                    }
                }
            })
    }
}

// ---------------------------------------------------------------- training

@Composable
fun TrainingScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    val club = w.userClub ?: return
    LazyColumn {
        item {
            SectionCard(vm.t("Fokus Latihan Tim", "Team Training Focus")) {
                for (f in TrainingFocus.entries) Row(Modifier.fillMaxWidth().clickable { vm.act { it.setTraining(f, club.trainingIntensity) } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(club.trainingFocus == f, { vm.act { it.setTraining(f, club.trainingIntensity) } }); Text(f.label(vm.lang))
                }
            }
        }
        item {
            SectionCard(vm.t("Intensitas", "Intensity")) {
                ChipRow(listOf(vm.t("Ringan", "Light"), vm.t("Normal", "Normal"), vm.t("Berat", "Heavy")), club.trainingIntensity - 1) { i -> vm.act { it.setTraining(club.trainingFocus, i + 1) } }
                SmallText(vm.t("Latihan berat mempercepat perkembangan tetapi menambah risiko cedera.", "Heavy training speeds development but raises injury risk."), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard(vm.t("Staf Pelatih & Fasilitas", "Coaching Staff & Facilities")) {
                KeyValue(vm.t("Fasilitas latihan", "Training facilities"), "${club.trainingFacilities}/20")
                KeyValue(vm.t("Kualitas pelatih", "Coaching quality"), "%.1f/20".format(fm.engine.development.Development.coachQuality(w, club, false)))
                KeyValue(vm.t("Pelatih kiper", "GK coaching"), "%.1f/20".format(fm.engine.development.Development.coachQuality(w, club, true)))
            }
        }
        item {
            SectionCard(vm.t("Perkembangan Pemain Muda", "Young Player Development")) {
                for (p in w.squad(club.id).filter { it.age(w.today) <= 21 }.sortedByDescending { it.pa }.take(10)) {
                    Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.PlayerDetail(p.id)) }.padding(vertical = 4.dp)) {
                        Box(Modifier.weight(1f)) { SmallText("${p.position.label(vm.lang)}  ${p.name} (${p.age(w.today)})") }
                        Stars(vm.career!!.potentialStars(p), 11.dp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- transfers & scouting

@Composable
fun TransfersScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val club = w.userClub
    var query by remember { mutableStateOf("") }
    var posIdx by remember { mutableIntStateOf(0) }
    var maxAge by remember { mutableFloatStateOf(35f) }
    var freeOnly by remember { mutableStateOf(false) }
    val posGroups = listOf<Set<Position>?>(null, setOf(Position.GK), setOf(Position.DC, Position.DR, Position.DL), setOf(Position.DM, Position.MC, Position.MR, Position.ML),
        setOf(Position.AMC, Position.AMR, Position.AML), setOf(Position.ST))
    val results = remember(query, posIdx, maxAge, freeOnly, vm.version) {
        Transfers.searchPlayers(w, posGroups[posIdx], maxAge.toInt(), nationId = -1, freeOnly = freeOnly, query = query).take(200)
    }
    val cols = listOf<Col<Player>>(
        Col(vm.t("Pos", "Pos"), 40.dp, { it.position.ordinal }) { SmallText(it.position.label(vm.lang), bold = true) },
        Col(vm.t("Nama", "Name"), Dp.Unspecified, { it.lastName }) {
            Column { SmallText(it.name, bold = true); SmallText(w.clubs[it.clubId]?.name ?: vm.t("Bebas", "Free"), MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        Col(vm.t("Usia", "Age"), 34.dp, { it.age(w.today) }) { SmallText("${it.age(w.today)}") },
        Col(vm.t("Nilai", "Value"), 76.dp, { it.value }) { SmallText(Money.format(it.value, vm.lang)) },
        Col(vm.t("Potensi", "Pot."), 64.dp, { c.potentialStars(it) }) { Stars(c.potentialStars(it), 10.dp) },
    )
    SortableTable(results, cols, key = { it.id }, onClick = { vm.navigate(Screen.PlayerDetail(it.id)) }, header = {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (club != null) Row(Modifier.padding(horizontal = 12.dp)) {
                SmallText(vm.t("Anggaran: ", "Budget: ") + Money.format(club.transferBudget, vm.lang), bold = true); Spacer(Modifier.width(12.dp))
                SmallText(if (Transfers.windowOpen(w)) vm.t("Jendela TERBUKA", "Window OPEN") else vm.t("Jendela tertutup (agen bebas tetap bisa)", "Window closed (free agents only)"),
                    if (Transfers.windowOpen(w)) Pal.win else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(query, { query = it }, label = { Text(vm.t("Cari nama", "Search name")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            ChipRow(listOf(vm.t("Semua", "All"), "GK", vm.t("Bek", "DEF"), vm.t("Gelandang", "MID"), vm.t("Sayap/AM", "AM"), vm.t("Penyerang", "FW")), posIdx) { posIdx = it }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(vm.t("Usia maks ", "Max age ") + maxAge.toInt(), Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
                Slider(maxAge, { maxAge = it }, valueRange = 16f..40f, modifier = Modifier.weight(1f))
                Checkbox(freeOnly, { freeOnly = it }); SmallText(vm.t("Bebas", "Free"))
            }
        }
    })
}

@Composable
fun ScoutingScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    LazyColumn {
        item {
            SectionCard(vm.t("Penugasan Pemandu Bakat", "Scouting Assignment")) {
                Text(vm.t("Pilih negara untuk dipantau. Semakin lama dipantau, semakin akurat atribut dan potensi yang terlihat.",
                    "Choose a nation to scout. The longer a region is watched, the more accurate attributes and potential become."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                val nations = w.nations
                ChipRow(listOf(vm.t("Tidak ada", "None")) + nations.map { it.name(vm.lang) },
                    nations.indexOfFirst { it.id == w.manager.scoutingNation } + 1) { i -> vm.act { it.assignScouts(if (i == 0) -1 else nations[i - 1].id) } }
                val scouts = w.userClub?.staffIds?.mapNotNull { w.staff[it] }?.filter { it.role == StaffRole.SCOUT } ?: emptyList()
                for (s in scouts) KeyValue(s.name, vm.t("Penilaian ", "Judging ") + "${s.judgingAbility}/${s.judgingPotential}")
                KeyValue(vm.t("Pemain dikenal", "Players known"), "${w.manager.knowledge.count { it.value >= 60 }}")
            }
        }
        item { Text(vm.t("  Daftar Pantauan", "  Shortlist"), style = MaterialTheme.typography.titleSmall) }
        items(w.manager.shortlist.mapNotNull { w.players[it] }, key = { it.id }) { p ->
            ListItem(modifier = Modifier.clickable { vm.navigate(Screen.PlayerDetail(p.id)) },
                headlineContent = { Text(p.name) },
                supportingContent = { Text("${p.position.label(vm.lang)} · ${p.age(w.today)} · ${w.clubs[p.clubId]?.name ?: vm.t("Bebas", "Free")} · ${Money.format(p.value, vm.lang)}") },
                trailingContent = { Stars(c.potentialStars(p), 11.dp) })
        }
        item {
            SectionCard(vm.t("Rekomendasi: talenta muda", "Recommendations: young talent")) {
                val recs = w.players.values.filter { it.age(w.today) <= 21 && c.knowledge(it) >= 50 && it.clubId != w.manager.clubId }
                    .sortedByDescending { c.potentialStars(it) }.take(10)
                for (p in recs) Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.PlayerDetail(p.id)) }.padding(vertical = 4.dp)) {
                    Box(Modifier.weight(1f)) { SmallText("${p.position.label(vm.lang)} ${p.name} (${p.age(w.today)}) — ${w.clubs[p.clubId]?.shortName ?: "-"}") }
                    Stars(c.potentialStars(p), 10.dp)
                }
                if (recs.isEmpty()) SmallText(vm.t("Tugaskan pemandu bakat untuk mendapat rekomendasi.", "Assign scouts to get recommendations."))
            }
        }
    }
}
