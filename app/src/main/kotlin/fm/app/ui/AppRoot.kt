package fm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.app.GameViewModel
import fm.app.Screen
import fm.core.*
import fm.engine.competition.CompetitionEngine
import fm.engine.dynamics.Dynamics

@Composable
fun AppRoot(vm: GameViewModel) {
    FmTheme(vm.theme) {
        vm.version // recompose when the world changes
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.toast) { vm.toast?.let { snackbar.showSnackbar(it); vm.toast = null } }
        BackHandler(enabled = vm.screen != Screen.Start && vm.screen != Screen.Home && vm.screen != Screen.MatchDayFlow) {
            if (!vm.back()) vm.navigate(Screen.Home, true)
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = vm.screen) {
                Screen.Start -> StartScreen(vm)
                Screen.NewCareer -> NewCareerScreen(vm)
                Screen.MatchDayFlow -> MatchDayScreen(vm)
                else -> if (vm.career == null) StartScreen(vm) else GameShell(vm, s)
            }
            if (vm.busy) BusyOverlay(vm)
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun BusyOverlay(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)).clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center) {
        Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp))
            Text(vm.t("Memproses dunia sepak bola…", "Processing the football world…"))
        } }
    }
}

data class NavItem(val screen: Screen, val id: String, val en: String, val icon: ImageVector, val group: Int)

val navItems = listOf(
    NavItem(Screen.Home, "Beranda", "Home", Icons.Filled.Home, 0),
    NavItem(Screen.Inbox, "Kotak Masuk", "Inbox", Icons.Filled.Email, 0),
    NavItem(Screen.Squad, "Skuad", "Squad", Icons.Filled.Person, 1),
    NavItem(Screen.Dynamics, "Dinamika", "Dynamics", Icons.Filled.Favorite, 1),
    NavItem(Screen.Tactics, "Taktik", "Tactics", Icons.Filled.Build, 1),
    NavItem(Screen.Training, "Latihan", "Training", Icons.Filled.Refresh, 1),
    NavItem(Screen.Competitions, "Jadwal & Kompetisi", "Fixtures & Competitions", Icons.Filled.DateRange, 2),
    NavItem(Screen.DataHub, "Pusat Data", "Data Hub", Icons.Filled.Info, 2),
    NavItem(Screen.International, "Internasional", "International", Icons.Filled.Star, 2),
    NavItem(Screen.Scouting, "Pencarian Bakat", "Scouting", Icons.Filled.Search, 2),
    NavItem(Screen.Transfers, "Transfer", "Transfers", Icons.Filled.ShoppingCart, 2),
    NavItem(Screen.Club, "Klub", "Club", Icons.Filled.Place, 3),
    NavItem(Screen.Finances, "Keuangan", "Finances", Icons.Filled.List, 3),
    NavItem(Screen.Staff, "Staf", "Staff", Icons.Filled.Face, 3),
    NavItem(Screen.Profile, "Manajer", "Manager", Icons.Filled.AccountCircle, 4),
    NavItem(Screen.Editor, "Editor", "Editor", Icons.Filled.Edit, 4),
    NavItem(Screen.Settings, "Pengaturan", "Settings", Icons.Filled.Settings, 4),
)

/** Sidebar + top bar + content, the main game layout (landscape). */
@Composable
private fun GameShell(vm: GameViewModel, screen: Screen) {
    val w = vm.world ?: return
    val fm = LocalFm.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Labelled sidebar only on large screens; phones get a compact icon rail.
        val expanded = maxWidth >= 1000.dp && maxHeight >= 560.dp
        Row(Modifier.fillMaxSize()) {
            // Sidebar
            Column(Modifier.width(if (expanded) 196.dp else 64.dp).fillMaxHeight().background(fm.sidebar).verticalScroll(rememberScrollState())) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClubBadge(w.userClub, if (expanded) 32.dp else 40.dp)
                    if (expanded) { Spacer(Modifier.width(8.dp)); Text(w.userClub?.shortName ?: "-", color = fm.sidebarText, fontWeight = FontWeight.Bold) }
                }
                var lastGroup = 0
                for (n in navItems) {
                    if (n.group != lastGroup) { HorizontalDivider(Modifier.padding(vertical = 4.dp), color = fm.sidebarText.copy(alpha = 0.15f)); lastGroup = n.group }
                    val sel = screen == n.screen || (screen is Screen.PlayerDetail && n.screen == Screen.Squad)
                    val unread = if (n.screen == Screen.Inbox) w.inbox.count { !it.read } else 0
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 0.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (sel) fm.sidebarSelected else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { vm.navigate(n.screen, root = true) }.padding(horizontal = 8.dp, vertical = if (expanded) 8.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("${minOf(unread, 99)}", fontSize = 9.sp) } }) {
                            Icon(n.icon, vm.t(n.id, n.en), tint = if (sel) androidx.compose.ui.graphics.Color.White else fm.sidebarText, modifier = Modifier.size(20.dp))
                        }
                        if (expanded) { Spacer(Modifier.width(10.dp)); Text(vm.t(n.id, n.en), color = if (sel) androidx.compose.ui.graphics.Color.White else fm.sidebarText, fontSize = 13.sp, maxLines = 1) }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(vm, screen)
                Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(vm, screen) }
            }
        }
    }
}

@Composable
private fun TopBar(vm: GameViewModel, screen: Screen) {
    val c = vm.career ?: return
    val w = c.world
    val fm = LocalFm.current
    val title = navItems.firstOrNull { it.screen == screen }?.let { vm.t(it.id, it.en) } ?: when (screen) {
        is Screen.PlayerDetail -> vm.t("Profil Pemain", "Player Profile")
        is Screen.ClubDetail -> vm.t("Profil Klub", "Club Profile")
        else -> ""
    }
    Row(Modifier.fillMaxWidth().height(if (LocalCompact.current) 46.dp else 56.dp).background(fm.topBar).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (navItems.none { it.screen == screen }) IconButton(onClick = { if (!vm.back()) vm.navigate(Screen.Home, true) }) { Icon(Icons.Filled.ArrowBack, null) }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(Cal.format(w.today, vm.lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(w.userClub?.let { w.comp(it.leagueId).name(vm.lang) } ?: vm.t("Tanpa klub", "Unemployed"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ContinueButton(vm)
    }
}

@Composable
fun ContinueButton(vm: GameViewModel) {
    val c = vm.career ?: return
    if (vm.matchDay != null) {
        Button(onClick = { vm.navigate(Screen.MatchDayFlow, root = true) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(vm.t("Kembali ke Laga", "Back to Match"))
        }
    } else if (c.userFixtureToday() != null) {
        Button(onClick = { vm.beginMatchDay() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(vm.t("Hari Pertandingan", "Match Day"))
        }
    } else {
        Button(onClick = { vm.continueGame() }) { Text(vm.t("Lanjutkan", "Continue")); Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.ArrowForward, null) }
    }
}

@Composable
private fun ScreenContent(vm: GameViewModel, screen: Screen) {
    val w = vm.world ?: return
    when (screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.Squad -> SquadScreen(vm)
        Screen.Dynamics -> DynamicsScreen(vm)
        Screen.Tactics -> TacticsScreen(vm)
        Screen.Transfers -> TransfersScreen(vm)
        Screen.Competitions -> CompetitionsScreen(vm)
        Screen.Training -> TrainingScreen(vm)
        Screen.Scouting -> ScoutingScreen(vm)
        Screen.Club -> ClubScreen(vm, w.manager.clubId)
        is Screen.ClubDetail -> ClubScreen(vm, screen.id)
        Screen.Finances -> FinanceScreen(vm)
        Screen.Staff -> StaffScreen(vm)
        Screen.International -> InternationalScreen(vm)
        Screen.DataHub -> DataHubScreen(vm)
        Screen.Editor -> EditorScreen(vm)
        Screen.Inbox -> InboxScreen(vm)
        Screen.Profile -> ProfileScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        is Screen.PlayerDetail -> PlayerScreen(vm, screen.id)
        else -> HomeScreen(vm)
    }
}

// ---------------------------------------------------------------- start & new career

@Composable
fun StartScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val fm = LocalFm.current
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().background(fm.sidebar).padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("⚽", fontSize = 56.sp)
            Text(vm.t("Manajer Bola", "Football Manager Android"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = fm.sidebarText)
            Text(vm.t("Simulasi manajemen sepak bola — offline", "Football management simulation — offline"), color = fm.sidebarText.copy(alpha = 0.7f))
            Spacer(Modifier.height(28.dp))
            Button(onClick = { vm.navigate(Screen.NewCareer) }, Modifier.width(280.dp).height(52.dp)) { Text(vm.t("Karier Baru", "New Career")) }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (l in Lang.entries) { FilterChip(vm.uiLang == l, { vm.setLang(l) }, { Text(if (l == Lang.ID) "Indonesia" else "English") }); Spacer(Modifier.width(6.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Row { for (t in ThemeChoice.entries) { FilterChip(vm.theme == t, { vm.setThemeChoice(t) }, { Text(t.label(vm.uiLang), fontSize = 11.sp) }); Spacer(Modifier.width(4.dp)) } }
            Spacer(Modifier.height(16.dp))
            SmallText(vm.t("Semua klub, pemain dan staf adalah fiktif.", "All clubs, players and staff are fictional."), fm.sidebarText.copy(alpha = 0.6f))
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            Text(vm.t("Lanjutkan Karier", "Continue Career"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            BackupControls(vm, canBackup = false)
            Spacer(Modifier.height(8.dp))
            val slots = vm.slots()
            if (slots.isEmpty()) SmallText(vm.t("Belum ada karier tersimpan.", "No saved careers yet."), MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn {
                items(slots) { s ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.load(s.slot) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${s.managerName} — ${s.clubName}", fontWeight = FontWeight.SemiBold)
                                SmallText("${Cal.format(s.day, vm.lang)} · ${vm.t("Musim", "Season")} ${s.season}/${(s.season + 1) % 100} · ${s.sizeBytes / 1024} KB", MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.deleteSlot(s.slot) }) { Icon(Icons.Filled.Delete, null) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewCareerScreen(vm: GameViewModel) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var seedText by remember { mutableStateOf((System.currentTimeMillis() % 1_000_000).toString()) }
    val seed = seedText.toLongOrNull() ?: 1L
    val preview = remember(seed, vm.pendingPack) { vm.previewWorld(seed, withPack = true) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPack(uri)
    }
    var formation by remember { mutableIntStateOf(0) }
    var attacking by remember { mutableFloatStateOf(0.6f) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var league by remember { mutableIntStateOf(0) }
    val leagues = preview.competitions.filter { it.type == CompType.LEAGUE }
    BackHandler { vm.navigate(Screen.Start, true) }
    val style = ManagerStyle(Formation.ALL[formation].name, attacking * 2.0 - 1.0, 0.5, 0.5, 0.6, 0.5)
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(340.dp).fillMaxHeight().background(LocalFm.current.panel).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(vm.t("Profil Manajer", "Manager Profile"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(first, { first = it }, label = { Text(vm.t("Nama depan", "First name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(last, { last = it }, label = { Text(vm.t("Nama belakang", "Last name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(seedText, { seedText = it.filter(Char::isDigit).take(9) }, label = { Text(vm.t("Seed dunia", "World seed")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(vm.t("Formasi favorit", "Preferred formation"), style = MaterialTheme.typography.labelLarge)
            ChipRow(Formation.ALL.map { it.name }, formation) { formation = it }
            LabeledSlider(vm.t("Filosofi bermain", "Playing philosophy"), attacking.toDouble(), vm.t("Bertahan", "Defensive"), vm.t("Menyerang", "Attacking")) { attacking = it.toFloat() }
            Spacer(Modifier.height(8.dp))
            Button(enabled = selected != null, onClick = { vm.newCareer(first.ifBlank { "Manajer" }, last, 1, selected, seed, style) },
                modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(vm.t("Mulai Karier", "Start Career")) }
            OutlinedButton(onClick = { vm.newCareer(first.ifBlank { "Manajer" }, last, 1, null, seed, style) }, modifier = Modifier.fillMaxWidth()) {
                Text(vm.t("Mulai tanpa klub", "Start unemployed"))
            }
            Spacer(Modifier.height(8.dp))
            Text(vm.t("Database", "Database"), style = MaterialTheme.typography.labelLarge)
            val pack = vm.pendingPack
            SmallText(pack?.let { "${it.name}: ${it.clubs.size} " + vm.t("klub", "clubs") + ", ${it.players.size} " + vm.t("pemain", "players") }
                ?: vm.t("Bawaan (fiktif)", "Default (fictional)"), MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/*", "text/csv", "*/*")) }) { Text(vm.t("Impor JSON/CSV", "Import JSON/CSV"), fontSize = 12.sp) }
                if (pack != null) TextButton(onClick = { vm.clearPack() }) { Text(vm.t("Hapus", "Clear"), fontSize = 12.sp) }
            }
            for (i in vm.packIssues.take(6)) SmallText((if (i.error) "✖ " else "⚠ ") + i.message, if (i.error) LocalFm.current.bad else LocalFm.current.warn)
            if (vm.packIssues.size > 6) SmallText("… +${vm.packIssues.size - 6}")
            TextButton(onClick = { vm.navigate(Screen.Start, true) }) { Text(vm.t("Kembali", "Back")) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Spacer(Modifier.height(8.dp))
            ChipRow(leagues.map { it.name(vm.lang) }, league) { league = it }
            val clubs = preview.clubs.values.filter { it.leagueId == leagues[league].id }.sortedByDescending { it.reputation }
            val ranks = clubs.mapIndexed { i, c -> c.id to i + 1 }.toMap()
            LazyVerticalGrid(GridCells.Adaptive(250.dp), Modifier.fillMaxSize().padding(8.dp)) {
                items(clubs, key = { it.id }) { c ->
                    val sel = selected == c.id
                    Card(Modifier.padding(4.dp).fillMaxWidth().clickable { selected = c.id },
                        colors = CardDefaults.cardColors(containerColor = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ClubBadge(c, 36.dp); Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                SmallText("${c.city} · ${vm.t("Rep", "Rep")} ${c.reputation} · ${vm.t("Target", "Target")} #${ranks[c.id]}", MaterialTheme.colorScheme.onSurfaceVariant)
                                SmallText(vm.t("Saldo ", "Balance ") + Money.format(c.balance, vm.lang), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sel) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- home dashboard

@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val fm = LocalFm.current
    Column(modifier.padding(5.dp).clip(RoundedCornerShape(10.dp)).background(fm.panel)) {
        if (title != null) Row(Modifier.fillMaxWidth().background(fm.header).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Column(Modifier.padding(10.dp), content = content)
    }
}

@Composable
fun HomeScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val club = w.userClub
    LazyVerticalGrid(GridCells.Adaptive(330.dp), Modifier.fillMaxSize().padding(6.dp)) {
        item {
            val f = c.userFixtureToday() ?: c.nextUserFixture()
            Panel(if (c.userFixtureToday() != null) vm.t("HARI PERTANDINGAN", "MATCH DAY") else vm.t("Laga Berikutnya", "Next Match")) {
                if (f == null) SmallText("-") else {
                    FixtureLine(vm, f)
                    SmallText(Cal.format(f.day, vm.lang), MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.userFixtureToday() != null) {
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.beginMatchDay() }, Modifier.fillMaxWidth()) { Text(vm.t("Menuju Pertandingan", "Go to Match")) }
                        OutlinedButton(onClick = { vm.playInstant() }, Modifier.fillMaxWidth()) { Text(vm.t("Hasil Instan", "Instant Result")) }
                    }
                }
            }
        }
        if (club == null) item {
            Panel(vm.t("Tanpa Klub", "Unemployed")) {
                Text(vm.t("Lanjutkan permainan untuk menerima tawaran kerja, lalu terima di menu Manajer.", "Continue to receive job offers, then accept them in Manager."))
                TextButton(onClick = { vm.navigate(Screen.Profile, true) }) { Text(vm.t("Lihat tawaran", "View offers")) }
            }
        } else {
            item {
                val pos = CompetitionEngine.position(w, club.id)
                Panel(vm.t("Status Klub", "Club Status")) {
                    KeyValue(vm.t("Posisi liga", "League position"), "$pos / ${w.comp(club.leagueId).league?.teams}")
                    KeyValue(vm.t("Target dewan", "Board expectation"), "#${club.expectedPosition}")
                    MeterRow(vm.t("Kepercayaan dewan", "Board confidence"), club.boardConfidence)
                    MeterRow(vm.t("Suasana ruang ganti", "Dressing room"), w.atmosphere)
                    MeterRow(vm.t("Kekompakan", "Cohesion"), Dynamics.cohesion(w, club.id))
                    Row(verticalAlignment = Alignment.CenterVertically) { SmallText(vm.t("Performa: ", "Form: ")); FormDots(club.form) }
                    KeyValue(vm.t("Saldo", "Balance"), Money.format(club.balance, vm.lang))
                }
            }
            item {
                val table = c.leagueTable(club.leagueId)
                val idx = table.indexOfFirst { it.clubId == club.id }
                Panel(vm.t("Klasemen", "League Table"), action = { TextButton(onClick = { vm.navigate(Screen.Competitions, true) }) { Text(vm.t("Lengkap", "Full")) } }) {
                    for (i in (idx - 3).coerceAtLeast(0)..(idx + 3).coerceAtMost(table.size - 1)) {
                        val r = table[i]
                        val me = r.clubId == club.id
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Box(Modifier.width(24.dp)) { SmallText("${i + 1}", bold = me) }
                            Box(Modifier.weight(1f)) { SmallText(w.club(r.clubId).name, bold = me) }
                            Box(Modifier.width(30.dp)) { SmallText("${r.played}", bold = me) }
                            Box(Modifier.width(34.dp)) { SmallText("${r.gd}", bold = me) }
                            Box(Modifier.width(30.dp)) { SmallText("${r.points}", bold = true) }
                        }
                    }
                }
            }
            item {
                Panel(vm.t("Perhatian Skuad", "Squad Attention"), action = { TextButton(onClick = { vm.navigate(Screen.Dynamics, true) }) { Text(vm.t("Dinamika", "Dynamics")) } }) {
                    val squad = w.squad(club.id)
                    val unhappy = squad.filter { it.happiness < 45 }.sortedBy { it.happiness }.take(4)
                    val injured = squad.filter { it.injuryDays > 0 }.take(4)
                    val expiring = squad.filter { (it.contract?.endYear ?: 9999) <= w.season + 1 && it.clubId == club.id }.take(3)
                    if (unhappy.isEmpty() && injured.isEmpty() && expiring.isEmpty()) SmallText(vm.t("Tidak ada masalah mendesak.", "Nothing urgent."))
                    for (p in unhappy) PlayerLineSmall(vm, p, "😟 " + Dynamics.happinessLabel(p.happiness, vm.lang))
                    for (p in injured) PlayerLineSmall(vm, p, "🩹 ${p.injuryName} (${p.injuryDays}h)")
                    for (p in expiring) PlayerLineSmall(vm, p, "📄 " + vm.t("Kontrak habis musim ini", "Contract expiring"))
                }
            }
        }
        item {
            Panel(vm.t("Kotak Masuk", "Inbox"), action = { TextButton(onClick = { vm.navigate(Screen.Inbox, true) }) { Text(vm.t("Semua", "All")) } }) {
                for (m in w.inbox.takeLast(6).reversed()) Text("• " + c.message(m), style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (m.read) FontWeight.Normal else FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp), maxLines = 2)
            }
        }
        item {
            Panel(vm.t("Berita Sepak Bola", "Football News")) {
                for (m in w.news.takeLast(6).reversed()) Text("• " + c.message(m), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp), maxLines = 2)
                if (w.news.isEmpty()) SmallText("-")
            }
        }
    }
}

@Composable
fun PlayerLineSmall(vm: GameViewModel, p: Player, note: String) {
    Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.PlayerDetail(p.id)) }.padding(vertical = 3.dp)) {
        Box(Modifier.width(36.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
        Box(Modifier.weight(1f)) { SmallText(p.name) }
        SmallText(note, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MeterRow(label: String, value: Int) {
    val fm = LocalFm.current
    val color = when { value >= 65 -> fm.good; value >= 40 -> fm.warn; else -> fm.bad }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.width(110.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text("$value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
    }
}

@Composable
fun FixtureLine(vm: GameViewModel, f: Fixture) {
    val w = vm.world ?: return
    val h = w.club(f.homeId); val a = w.club(f.awayId)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClubBadge(h); Spacer(Modifier.width(6.dp))
        Text(h.name, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold)
        Text(if (f.played) "${f.homeGoals} - ${f.awayGoals}" else "vs", Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
        Text(a.name, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Spacer(Modifier.width(6.dp)); ClubBadge(a)
    }
    SmallText(w.comp(f.compId).name(vm.lang) + (if (f.leg == 2) vm.t(" · Leg 2", " · 2nd leg") else ""), MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- squad

@Composable
fun SquadScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val club = w.userClub ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    var team by remember { mutableIntStateOf(0) }
    val squad = w.squad(club.id).filter { it.reserve == (team == 1) }
    val ref = w.squad(club.id).map { it.ca }.sortedDescending().take(11).average().takeIf { !it.isNaN() } ?: 100.0
    Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChipRow(listOf(vm.t("Tim Utama", "First Team") + " (${w.squad(club.id).count { !it.reserve }})", vm.t("Cadangan / U21", "Reserves / U21") + " (${w.squad(club.id).count { it.reserve }})"), team) { team = it }
        if (team == 1 && club.leagueId == fm.engine.game.Reserves.RESERVE_LEAGUE) {
            val t = fm.engine.game.Reserves.table(w)
            val pos = t.indexOfFirst { it.clubId == club.id } + 1
            SmallText(if (t.isEmpty()) vm.t("Liga Cadangan: belum dimulai", "Reserve league: not started") else vm.t("Liga Cadangan: posisi ", "Reserve league: position ") + "$pos/${t.size}" + (t.firstOrNull { it.clubId == club.id }?.let { " · ${it.points} " + vm.t("poin", "pts") } ?: ""),
                MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    BoxWithConstraints {
        val wide = maxWidth >= 760.dp
        val cols = mutableListOf<Col<Player>>(
            Col(vm.t("Pos", "Pos"), 44.dp, { -it.position.ordinal }) { SmallText(it.position.label(vm.lang), bold = true) },
            Col(vm.t("Nama", "Name"), Dp.Unspecified, { it.lastName }) {
                Column {
                    SmallText(it.name, bold = true)
                    val status = when { it.injuryDays > 0 -> "🩹 ${it.injuryDays}h"; it.suspended > 0 -> vm.t("Skorsing", "Suspended"); it.wantsOut -> vm.t("Minta dijual", "Wants out")
                        it.transferListed -> vm.t("Dijual", "Listed"); else -> "" }
                    if (status.isNotEmpty()) SmallText(status, MaterialTheme.colorScheme.error)
                }
            },
            Col(vm.t("Usia", "Age"), 38.dp, { it.age(w.today) }) { SmallText("${it.age(w.today)}") },
            Col(vm.t("Kemampuan", "Ability"), 76.dp, { it.ca }) { Stars(Ability.starsFor(it.ca, ref), 11.dp) },
        )
        if (wide) cols += Col(vm.t("Potensi", "Potential"), 76.dp, { c.potentialStars(it) }) { Stars(c.potentialStars(it), 11.dp) }
        cols += Col(vm.t("Kondisi", "Cond."), 52.dp, { it.condition }) { SmallText("${it.condition}%", if (it.condition < 75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
        cols += Col(vm.t("Moral", "Morale"), 48.dp, { it.morale }) { SmallText(moraleLabel(it.morale)) }
        if (wide) {
            cols += Col(vm.t("Main", "Apps"), 40.dp, { it.season.apps }) { SmallText("${it.season.apps}") }
            cols += Col(vm.t("Gol", "Gls"), 36.dp, { it.season.goals }) { SmallText("${it.season.goals}") }
            cols += Col(vm.t("Rata", "Av Rat"), 46.dp, { it.season.avgRating }) { SmallText(if (it.season.apps > 0) "%.2f".format(it.season.avgRating) else "-") }
            cols += Col(vm.t("Nilai", "Value"), 76.dp, { it.value }) { SmallText(Money.format(it.value, vm.lang)) }
            cols += Col(vm.t("Gaji", "Wage"), 72.dp, { it.contract?.wage ?: 0L }) { SmallText(Money.format(it.contract?.wage ?: 0, vm.lang)) }
            cols += Col(vm.t("Kontrak", "Contract"), 56.dp, { it.contract?.endYear ?: 0 }) { SmallText("${it.contract?.endYear ?: "-"}") }
        }
        if (team == 1) cols += Col(vm.t("Main Cad.", "Res Apps"), 60.dp, { it.reserveApps }) { SmallText("${it.reserveApps} / ${it.reserveGoals}") }
        SortableTable(squad, cols, key = { it.id }, onClick = { vm.navigate(Screen.PlayerDetail(it.id)) }, defaultSort = 0)
    }
    }
}

fun moraleLabel(m: Int) = when { m >= 85 -> "😄"; m >= 65 -> "🙂"; m >= 45 -> "😐"; m >= 25 -> "🙁"; else -> "😠" }

object Ability {
    fun starsFor(ca: Int, ref: Double) = fm.engine.ability.Ability.stars(ca, ref)
}
