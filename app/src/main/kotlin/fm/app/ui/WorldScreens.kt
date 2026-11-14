package fm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fm.app.GameViewModel
import fm.app.Screen
import fm.core.*
import fm.engine.finance.Finance

// ---------------------------------------------------------------- competitions

@Composable
fun CompetitionsScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val comps = w.competitions
    var sel by remember { mutableIntStateOf(comps.indexOfFirst { it.id == (w.userClub?.leagueId ?: comps.first().id) }.coerceAtLeast(0)) }
    var tab by remember { mutableIntStateOf(0) }
    val comp = comps[sel]
    val cs = w.compSeasons[comp.id]
    LazyColumn(Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(6.dp)); ChipRow(comps.map { it.shortName }, sel) { sel = it; tab = 0 } }
        item {
            Text(comp.name(vm.lang) + " ${w.season}/${(w.season + 1) % 100}", Modifier.padding(12.dp, 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (comp.type == CompType.LEAGUE) ChipRow(listOf(vm.t("Klasemen", "Table"), vm.t("Jadwal & Hasil", "Fixtures"), vm.t("Sejarah", "History")), tab) { tab = it }
            else ChipRow(listOf(vm.t("Babak", "Rounds"), vm.t("Sejarah", "History")), if (tab == 2) 1 else 0) { tab = if (it == 1) 2 else 0 }
        }
        if (cs == null) return@LazyColumn
        if (comp.type == CompType.LEAGUE && tab == 0) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SmallText("#", bold = true); Spacer(Modifier.width(18.dp))
                    Box(Modifier.weight(1f)) { SmallText(vm.t("Klub", "Club"), bold = true) }
                    for (h in listOf("M", "SG", "Poin")) Box(Modifier.width(36.dp)) { SmallText(h, bold = true) }
                }
            }
            val table = c.leagueTable(comp.id)
            val rules = comp.league!!
            items(table.size) { i ->
                val r = table[i]
                val club = w.club(r.clubId)
                val zone = when {
                    rules.promote > 0 && i < rules.promote -> Pal.win
                    rules.relegate > 0 && i >= table.size - rules.relegate -> Pal.loss
                    else -> null
                }
                Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.ClubDetail(club.id)) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(22.dp)) { SmallText("${i + 1}", zone ?: MaterialTheme.colorScheme.onSurface, bold = true) }
                    ClubBadge(club, 20.dp); Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) { SmallText(club.name, bold = club.id == w.manager.clubId) }
                    Box(Modifier.width(36.dp)) { SmallText("${r.played}") }
                    Box(Modifier.width(36.dp)) { SmallText("${r.gd}") }
                    Box(Modifier.width(36.dp)) { SmallText("${r.points}", bold = true) }
                }
            }
        } else if (tab == 2) {
            items(w.seasonHistory.reversed()) { s ->
                ListItem(headlineContent = { Text("${s.season}/${(s.season + 1) % 100}: ${s.champions[comp.id] ?: "-"}") },
                    supportingContent = { Text(vm.t("Top skor: ", "Top scorer: ") + s.topScorer) })
            }
        } else {
            val byRound = cs.fixtures.groupBy { it.round }.toSortedMap()
            for ((round, fx) in byRound) {
                item { Text((if (comp.type == CompType.LEAGUE) vm.t("Pekan ", "Matchday ") else vm.t("Babak ", "Round ")) + round + " · " + Cal.formatShort(fx.first().day, vm.lang),
                    Modifier.padding(12.dp, 8.dp, 12.dp, 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                items(fx, key = { it.id }) { f ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
                        Box(Modifier.weight(1f)) { SmallText(w.club(f.homeId).name, bold = f.homeId == w.manager.clubId) }
                        SmallText(if (f.played) " ${f.homeGoals}-${f.awayGoals}" + (if (f.penHome >= 0) " (p ${f.penHome}-${f.penAway})" else "") + " " else " - ", bold = true)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { SmallText(w.club(f.awayId).name, bold = f.awayId == w.manager.clubId) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ---------------------------------------------------------------- club

@Composable
fun ClubScreen(vm: GameViewModel, clubId: Long) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    val club = w.clubs[clubId] ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    LazyVerticalGrid(GridCells.Adaptive(380.dp), Modifier.fillMaxSize().padding(6.dp)) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClubBadge(club, 48.dp); Spacer(Modifier.width(12.dp))
                    Column {
                        Text(club.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${club.city} · ${vm.t("Berdiri", "Founded")} ${club.founded}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                KeyValue(vm.t("Liga", "League"), w.comp(club.leagueId).name(vm.lang))
                KeyValue(vm.t("Reputasi", "Reputation"), "${club.reputation}/100")
                KeyValue(vm.t("Stadion", "Stadium"), "${club.stadiumName} (${club.stadiumCapacity})")
                KeyValue(vm.t("Basis suporter", "Fan base"), "${club.fanBase}")
                KeyValue(vm.t("Fasilitas latihan / akademi", "Training / youth facilities"), "${club.trainingFacilities} / ${club.youthFacilities}")
                KeyValue(vm.t("Rekrutmen pemain muda", "Youth recruitment"), "${club.youthRecruitment}")
                KeyValue(vm.t("Target dewan", "Board expectation"), "#${club.expectedPosition}")
                KeyValue(vm.t("Kepercayaan dewan", "Board confidence"), "${club.boardConfidence}%")
                Row(verticalAlignment = Alignment.CenterVertically) { SmallText(vm.t("Performa: ", "Form: ")); FormDots(club.form) }
            }
        }
        item {
            SectionCard(vm.t("Staf", "Staff")) {
                for (s in club.staffIds.mapNotNull { w.staff[it] }.sortedBy { it.role.ordinal })
                    KeyValue("${s.role.label(vm.lang)}", "${s.name} (${"%.0f".format(s.quality())})")
                if (club.id == w.manager.clubId) KeyValue(StaffRole.MANAGER.label(vm.lang), w.manager.name)
            }
        }
        item {
            SectionCard(vm.t("Prestasi", "Honours")) {
                if (club.honours.isEmpty()) SmallText("-")
                for (h in club.honours.takeLast(15).reversed()) SmallText("🏆 " + h.replace(":", " "))
            }
        }
        if (club.id != w.manager.clubId) item {
            SectionCard(vm.t("Skuad", "Squad")) {
                for (p in w.squad(club.id).sortedByDescending { it.ca }.take(25))
                    Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.PlayerDetail(p.id)) }.padding(vertical = 3.dp)) {
                        Box(Modifier.width(40.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
                        Box(Modifier.weight(1f)) { SmallText(p.name) }
                        SmallText("${p.age(w.today)}")
                    }
            }
        }
    }
}

// ---------------------------------------------------------------- finances

@Composable
fun FinanceScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    val club = w.userClub ?: return
    val f = club.finance
    LazyVerticalGrid(GridCells.Adaptive(380.dp), Modifier.fillMaxSize().padding(6.dp)) {
        item {
            SectionCard(vm.t("Ringkasan", "Overview")) {
                KeyValue(vm.t("Saldo", "Balance"), Money.format(club.balance, vm.lang), if (club.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                KeyValue(vm.t("Anggaran transfer", "Transfer budget"), Money.format(club.transferBudget, vm.lang))
                KeyValue(vm.t("Anggaran gaji / bulan", "Wage budget / month"), Money.format(club.wageBudget, vm.lang))
                KeyValue(vm.t("Gaji pemain / bulan", "Player wages / month"), Money.format(Finance.playerWageBill(w, club), vm.lang))
                KeyValue(vm.t("Gaji staf / bulan", "Staff wages / month"), Money.format(Finance.staffWageBill(w, club), vm.lang))
                KeyValue(vm.t("Perkiraan pendapatan tahunan", "Estimated annual revenue"), Money.format(Finance.estimatedAnnualRevenue(w, club), vm.lang))
            }
        }
        item {
            SectionCard(vm.t("Saldo bulanan", "Monthly balance")) {
                val pts = f.monthly.map { it[1].toFloat() }
                if (pts.size >= 2) {
                    val color = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        val min = pts.min(); val max = pts.max()
                        val span = (max - min).takeIf { it > 0 } ?: 1f
                        val path = Path()
                        pts.forEachIndexed { i, v ->
                            val x = size.width * i / (pts.size - 1)
                            val y = size.height - (v - min) / span * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = Stroke(4f))
                        if (min < 0 && max > 0) { val y0 = size.height - (0 - min) / span * size.height; drawLine(color.copy(alpha = 0.3f), Offset(0f, y0), Offset(size.width, y0)) }
                    }
                } else SmallText(vm.t("Data tersedia setelah satu bulan.", "Data available after one month."))
            }
        }
        item {
            SectionCard(vm.t("Musim ini", "This season")) {
                fun label(k: String) = when (k) {
                    Finance.TV -> vm.t("Hak siar", "Broadcasting"); Finance.MATCHDAY -> vm.t("Tiket pertandingan", "Matchday")
                    Finance.COMMERCIAL -> vm.t("Sponsor & merchandise", "Sponsorship & merchandise"); Finance.PRIZE -> vm.t("Hadiah", "Prize money")
                    Finance.SALES -> vm.t("Penjualan pemain", "Player sales"); Finance.OWNER -> vm.t("Suntikan pemilik", "Owner injection")
                    Finance.WAGES -> vm.t("Gaji pemain", "Player wages"); Finance.STAFF -> vm.t("Gaji staf", "Staff wages")
                    Finance.UPKEEP -> vm.t("Operasional & fasilitas", "Operations & facilities"); Finance.PURCHASES -> vm.t("Pembelian pemain", "Player purchases")
                    Finance.AGENT -> vm.t("Biaya agen", "Agent fees"); else -> k
                }
                Text(vm.t("Pemasukan", "Income"), fontWeight = FontWeight.Bold)
                for ((k, v) in f.income) KeyValue(label(k), Money.format(v, vm.lang), Pal.win)
                Spacer(Modifier.height(6.dp))
                Text(vm.t("Pengeluaran", "Expenses"), fontWeight = FontWeight.Bold)
                for ((k, v) in f.expense) KeyValue(label(k), Money.format(v, vm.lang), Pal.loss)
            }
        }
        item {
            SectionCard(vm.t("Riwayat musim", "Season history")) {
                for (s in f.seasons.takeLast(10).reversed()) KeyValue("${s[0]}/${(s[0] + 1) % 100}", "${Money.format(s[1], vm.lang)} / ${Money.format(s[2], vm.lang)} → ${Money.format(s[3], vm.lang)}")
                if (f.seasons.isEmpty()) SmallText("-")
            }
        }
    }
}

// ---------------------------------------------------------------- inbox, profile, settings

@Composable
fun InboxScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val msgs = c.world.inbox.reversed()
    LaunchedEffect(Unit) { vm.act { cc -> cc.world.inbox.forEach { it.read = true } } }
    LazyColumn {
        items(msgs, key = { it.id }) { m ->
            ListItem(
                modifier = Modifier.clickable {
                    val ref = m.ref
                    if (ref >= 0 && ref in c.world.players) vm.navigate(Screen.PlayerDetail(ref))
                    else if (ref >= 0 && ref in c.world.clubs) vm.navigate(Screen.ClubDetail(ref))
                },
                overlineContent = { Text(Cal.format(m.day, vm.lang)) },
                headlineContent = { Text(c.message(m), fontWeight = if (m.read) FontWeight.Normal else FontWeight.Bold) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun ProfileScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val m = w.manager
    LazyVerticalGrid(GridCells.Adaptive(380.dp), Modifier.fillMaxSize().padding(6.dp)) {
        item {
            SectionCard(m.name) {
                KeyValue(vm.t("Kebangsaan", "Nationality"), w.nation(m.nationId).name(vm.lang))
                KeyValue(vm.t("Lisensi", "Qualification"), m.qualification)
                KeyValue(vm.t("Pengalaman bermain", "Playing experience"), m.playingExperience)
                KeyValue(vm.t("Formasi favorit", "Preferred formation"), m.style.preferredFormation)
                KeyValue(vm.t("Reputasi", "Reputation"), "${m.reputation}/100")
                KeyValue(vm.t("Klub", "Club"), w.userClub?.name ?: vm.t("Tanpa klub", "Unemployed"))
                KeyValue(vm.t("Laga (M-S-K)", "Matches (W-D-L)"), "${m.matches} (${m.wins}-${m.draws}-${m.losses})")
                KeyValue(vm.t("Pernah dipecat", "Sackings"), "${m.sackings}")
            }
        }
        if (m.clubId < 0) item {
            SectionCard(vm.t("Tawaran Pekerjaan", "Job Offers")) {
                if (m.jobOffers.isEmpty()) SmallText(vm.t("Belum ada tawaran. Lanjutkan permainan.", "No offers yet. Keep playing."))
                for (id in m.jobOffers.toList()) {
                    val club = w.club(id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClubBadge(club); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) { Text(club.name); SmallText(w.comp(club.leagueId).name(vm.lang)) }
                        Button(onClick = { vm.act { it.acceptJob(id) } }) { Text(vm.t("Terima", "Accept")) }
                    }
                }
            }
        }
        item {
            SectionCard(vm.t("Prestasi", "Achievements")) {
                if (m.achievements.isEmpty()) SmallText("-")
                for (a in m.achievements.reversed()) SmallText("🏆 $a")
            }
        }
        item {
            SectionCard(vm.t("Riwayat Karier", "Career History")) {
                if (m.history.isEmpty()) SmallText("-")
                for (h in m.history.reversed()) KeyValue("${h.season}/${(h.season + 1) % 100} ${h.clubName}", if (h.leaguePosition > 0) "#${h.leaguePosition} ${h.note}" else h.note)
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    LazyVerticalGrid(GridCells.Adaptive(380.dp), Modifier.fillMaxSize().padding(6.dp)) {
        item {
            SectionCard(vm.t("Simpan", "Save")) {
                KeyValue(vm.t("Slot", "Slot"), vm.slot)
                Button(onClick = { vm.save() }, Modifier.fillMaxWidth()) { Text(vm.t("Simpan sekarang", "Save now")) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.t("Simpan otomatis", "Autosave"), Modifier.weight(1f)); Switch(w.settings.autosave, { v -> vm.act { it.world.settings.autosave = v } })
                }
                OutlinedButton(onClick = { vm.save(false); vm.navigate(Screen.Start, true) }, Modifier.fillMaxWidth()) { Text(vm.t("Simpan & ke menu utama", "Save & main menu")) }
            }
        }
        item {
            SectionCard(vm.t("Tampilan", "Display")) {
                Text(vm.t("Tema", "Theme"), style = MaterialTheme.typography.labelLarge)
                for (t in ThemeChoice.entries) Row(Modifier.fillMaxWidth().clickable { vm.setThemeChoice(t) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(vm.theme == t, { vm.setThemeChoice(t) }); Text(t.label(vm.lang))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.t("Bahasa", "Language"), Modifier.weight(1f))
                    FilterChip(vm.lang == Lang.ID, { vm.setLang(Lang.ID) }, { Text("ID") }); Spacer(Modifier.width(6.dp))
                    FilterChip(vm.lang == Lang.EN, { vm.setLang(Lang.EN) }, { Text("EN") })
                }
            }
        }
        item {
            SectionCard(vm.t("Simulasi", "Simulation")) {
                Text(vm.t("Liga detail (model REDUCED). Liga lain disimulasikan ringan (LIGHT) untuk menghemat baterai.",
                    "Detailed leagues (REDUCED model). Other leagues use the LIGHT model to save battery."), style = MaterialTheme.typography.bodySmall)
                for (n in w.nations.filter { nat -> w.competitions.any { it.nationId == nat.id && it.type == CompType.LEAGUE } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.name(vm.lang), Modifier.weight(1f))
                        Switch(n.id in w.settings.detailedNations, { on -> vm.act { cc -> val l = cc.world.settings.detailedNations; if (on) { if (n.id !in l) l.add(n.id) } else l.remove(n.id) } })
                    }
                }
                KeyValue(vm.t("Pemain aktif di dunia", "Active players in world"), "${w.players.size}")
                KeyValue(vm.t("Pemain pensiun tercatat", "Archived retired players"), "${w.archive.size}")
            }
        }
    }
}
