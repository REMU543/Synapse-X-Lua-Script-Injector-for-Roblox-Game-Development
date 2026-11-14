package fm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fm.app.GameViewModel
import fm.app.Screen
import fm.core.*
import fm.engine.competition.CompetitionEngine
import fm.engine.game.Career

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: GameViewModel) {
    FmTheme(vm.dark) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.toast) { vm.toast?.let { snackbar.showSnackbar(it); vm.toast = null } }
        BackHandler(enabled = vm.screen != Screen.Start && vm.screen != Screen.Home) { if (!vm.back()) vm.navigate(Screen.Home, true) }
        @Suppress("UNUSED_VARIABLE") val v = vm.version // recompose on world changes
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = vm.screen) {
                Screen.Start -> StartScreen(vm)
                Screen.NewCareer -> NewCareerScreen(vm)
                Screen.Match -> MatchScreen(vm)
                else -> if (vm.career == null) StartScreen(vm) else GameScaffold(vm, s, snackbar)
            }
            if (vm.busy) BusyOverlay(vm)
        }
        if (vm.screen == Screen.Start || vm.screen == Screen.NewCareer) SnackbarHost(snackbar)
    }
}

@Composable
private fun BusyOverlay(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(vm.t("Memproses dunia sepak bola…", "Simulating the football world…"))
        } }
    }
}

private data class NavItem(val screen: Screen, val id: String, val en: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScaffold(vm: GameViewModel, screen: Screen, snackbar: SnackbarHostState) {
    val w = vm.world ?: return
    val bottom = listOf(
        NavItem(Screen.Home, "Beranda", "Home", Icons.Filled.Home),
        NavItem(Screen.Squad, "Skuad", "Squad", Icons.Filled.Person),
        NavItem(Screen.Tactics, "Taktik", "Tactics", Icons.Filled.Build),
        NavItem(Screen.Transfers, "Transfer", "Transfers", Icons.Filled.ShoppingCart),
        NavItem(Screen.Competitions, "Kompetisi", "Competitions", Icons.Filled.List),
    )
    val more = listOf(
        NavItem(Screen.Training, "Latihan", "Training", Icons.Filled.Refresh),
        NavItem(Screen.Scouting, "Pencarian Bakat", "Scouting", Icons.Filled.Search),
        NavItem(Screen.Club, "Klub", "Club", Icons.Filled.Place),
        NavItem(Screen.Finances, "Keuangan", "Finances", Icons.Filled.Info),
        NavItem(Screen.Inbox, "Kotak Masuk", "Inbox", Icons.Filled.Email),
        NavItem(Screen.Profile, "Profil Manajer", "Manager Profile", Icons.Filled.AccountCircle),
        NavItem(Screen.Settings, "Simpan & Pengaturan", "Save & Settings", Icons.Filled.Settings),
    )
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(w.userClub?.name ?: vm.t("Tanpa Klub", "Unemployed"), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(Cal.format(w.today, vm.lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { if (bottom.none { it.screen == screen }) IconButton(onClick = { if (!vm.back()) vm.navigate(Screen.Home, true) }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    val unread = w.inbox.count { !it.read }
                    IconButton(onClick = { vm.navigate(Screen.Inbox) }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("${minOf(unread, 99)}") } }) { Icon(Icons.Filled.Email, null) }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        for (m in more) DropdownMenuItem(text = { Text(vm.t(m.id, m.en)) }, leadingIcon = { Icon(m.icon, null) },
                            onClick = { menu = false; vm.navigate(m.screen) })
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for (b in bottom) NavigationBarItem(selected = screen == b.screen, onClick = { vm.navigate(b.screen, root = true) },
                    icon = { Icon(b.icon, null) }, label = { Text(vm.t(b.id, b.en), maxLines = 1) })
            }
        },
        floatingActionButton = {
            if (screen == Screen.Home || screen == Screen.Squad || screen == Screen.Competitions) ContinueButton(vm)
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Squad -> SquadScreen(vm)
                Screen.Tactics -> TacticsScreen(vm)
                Screen.Transfers -> TransfersScreen(vm)
                Screen.Competitions -> CompetitionsScreen(vm)
                Screen.Training -> TrainingScreen(vm)
                Screen.Scouting -> ScoutingScreen(vm)
                Screen.Club -> ClubScreen(vm, w.manager.clubId)
                is Screen.ClubDetail -> ClubScreen(vm, screen.id)
                Screen.Finances -> FinanceScreen(vm)
                Screen.Inbox -> InboxScreen(vm)
                Screen.Profile -> ProfileScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.PlayerDetail -> PlayerScreen(vm, screen.id)
                else -> HomeScreen(vm)
            }
        }
    }
}

@Composable
fun ContinueButton(vm: GameViewModel) {
    val c = vm.career ?: return
    val today = c.userFixtureToday()
    if (today != null) {
        ExtendedFloatingActionButton(onClick = { vm.navigate(Screen.Home, true) }, icon = { Icon(Icons.Filled.PlayArrow, null) },
            text = { Text(vm.t("Hari Pertandingan", "Match Day")) }, containerColor = MaterialTheme.colorScheme.secondary)
    } else {
        ExtendedFloatingActionButton(onClick = { vm.continueGame() }, icon = { Icon(Icons.Filled.ArrowForward, null) },
            text = { Text(vm.t("Lanjutkan", "Continue")) })
    }
}

// ---------------------------------------------------------------- start & new career

@Composable
fun StartScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    @Suppress("UNUSED_VARIABLE") val v = vm.version
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text("⚽", style = MaterialTheme.typography.displayLarge)
        Text(vm.t("Manajer Bola", "Football Manager Android"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(vm.t("Simulasi manajemen sepak bola offline", "Offline football management simulation"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { vm.navigate(Screen.NewCareer) }, Modifier.fillMaxWidth().height(52.dp)) { Text(vm.t("Karier Baru", "New Career")) }
        Spacer(Modifier.height(16.dp))
        val slots = vm.slots()
        if (slots.isNotEmpty()) Text(vm.t("Lanjutkan Karier", "Continue Career"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
        for (s in slots) {
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
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vm.t("Bahasa", "Language") + ": ")
            FilterChip(vm.uiLang == Lang.ID, { vm.setLang(Lang.ID) }, { Text("Indonesia") })
            Spacer(Modifier.width(8.dp))
            FilterChip(vm.uiLang == Lang.EN, { vm.setLang(Lang.EN) }, { Text("English") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vm.t("Tema gelap", "Dark theme")); Switch(vm.dark, { vm.dark = it })
        }
        Spacer(Modifier.height(12.dp))
        SmallText(vm.t("Semua klub, pemain dan staf adalah fiktif.", "All clubs, players and staff are fictional."), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NewCareerScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var seedText by remember { mutableStateOf((System.currentTimeMillis() % 1_000_000).toString()) }
    val seed = seedText.toLongOrNull() ?: 1L
    val preview = remember(seed) { vm.previewWorld(seed) }
    var formation by remember { mutableIntStateOf(0) }
    var attacking by remember { mutableFloatStateOf(0.2f) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var league by remember { mutableIntStateOf(0) }
    val leagues = preview.competitions.filter { it.type == CompType.LEAGUE }
    BackHandler { vm.navigate(Screen.Start, true) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(vm.t("Profil Manajer", "Manager Profile"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(first, { first = it }, label = { Text(vm.t("Nama depan", "First name")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(last, { last = it }, label = { Text(vm.t("Nama belakang", "Last name")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(seedText, { seedText = it.filter(Char::isDigit).take(9) }, label = { Text(vm.t("Seed dunia", "World seed")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(vm.t("Formasi favorit", "Preferred formation"), style = MaterialTheme.typography.labelLarge)
            }
        }
        item { ChipRow(Formation.ALL.map { it.name }, formation) { formation = it } }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LabeledSlider(vm.t("Gaya bermain", "Playing style"), attacking.toDouble(), vm.t("Bertahan", "Defensive"), vm.t("Menyerang", "Attacking")) { attacking = it.toFloat() }
                Text(vm.t("Pilih klub", "Choose a club"), style = MaterialTheme.typography.titleMedium)
            }
        }
        item { ChipRow(leagues.map { it.shortName }, league) { league = it } }
        val clubs = preview.clubs.values.filter { it.leagueId == leagues[league].id }.sortedByDescending { it.reputation }
        items(clubs, key = { it.id }) { c ->
            ListItem(
                modifier = Modifier.clickable { selected = c.id },
                headlineContent = { Text(c.name) },
                supportingContent = { Text("${c.city} · ${vm.t("Reputasi", "Reputation")} ${c.reputation} · ${vm.t("Target", "Target")} #${c.expectedPositionOrRank(preview)}") },
                leadingContent = { ClubBadge(c) },
                trailingContent = { if (selected == c.id) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            )
        }
        item {
            Column(Modifier.padding(16.dp)) {
                val style = ManagerStyle(Formation.ALL[formation].name, attacking * 2.0 - 1.0, 0.5, 0.5, 0.6, 0.5)
                Button(enabled = selected != null, onClick = {
                    vm.newCareer(first.ifBlank { "Manajer" }, last, 1, selected, seed, style)
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(vm.t("Mulai Karier", "Start Career")) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.newCareer(first.ifBlank { "Manajer" }, last, 1, null, seed, style) },
                    modifier = Modifier.fillMaxWidth()) { Text(vm.t("Mulai tanpa klub (melamar pekerjaan)", "Start unemployed (apply for jobs)")) }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private fun Club.expectedPositionOrRank(w: World): Int =
    w.clubs.values.filter { it.leagueId == leagueId }.sortedByDescending { it.reputation }.indexOfFirst { it.id == id } + 1

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val club = w.userClub
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val f = c.userFixtureToday()
            if (f != null) {
                SectionCard(vm.t("HARI PERTANDINGAN", "MATCH DAY")) {
                    FixtureLine(vm, f)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { vm.startMatch() }, Modifier.weight(1f)) { Text(vm.t("Tonton Laga", "Watch Match")) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.playInstant() }, Modifier.weight(1f)) { Text(vm.t("Hasil Instan", "Instant Result")) }
                    }
                }
            } else c.nextUserFixture()?.let { nf ->
                SectionCard(vm.t("Laga Berikutnya", "Next Match")) { FixtureLine(vm, nf); SmallText(Cal.format(nf.day, vm.lang), MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (club == null) item {
            SectionCard(vm.t("Tanpa Klub", "Unemployed")) {
                Text(vm.t("Lanjutkan permainan untuk menerima tawaran kerja, lalu terima di Profil.", "Continue to receive job offers, then accept them in Profile."))
                TextButton(onClick = { vm.navigate(Screen.Profile) }) { Text(vm.t("Buka tawaran", "Open offers")) }
            }
        } else item {
            val pos = CompetitionEngine.position(w, club.id)
            SectionCard(w.comp(club.leagueId).name(vm.lang)) {
                KeyValue(vm.t("Posisi liga", "League position"), "$pos / ${w.comp(club.leagueId).league?.teams}")
                KeyValue(vm.t("Target dewan", "Board target"), "#${club.expectedPosition}")
                KeyValue(vm.t("Kepercayaan dewan", "Board confidence"), "${club.boardConfidence}%",
                    if (club.boardConfidence < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) { SmallText(vm.t("Performa: ", "Form: ")); FormDots(club.form) }
                KeyValue(vm.t("Saldo", "Balance"), Money.format(club.balance, vm.lang))
            }
        }
        if (club != null) item {
            val table = c.leagueTable(club.leagueId)
            val idx = table.indexOfFirst { it.clubId == club.id }
            SectionCard(vm.t("Klasemen", "Table")) {
                for (i in (idx - 2).coerceAtLeast(0)..(idx + 2).coerceAtMost(table.size - 1)) {
                    val r = table[i]
                    Row(Modifier.fillMaxWidth()) {
                        SmallText("${i + 1}.", bold = r.clubId == club.id); Spacer(Modifier.width(6.dp))
                        Box(Modifier.weight(1f)) { SmallText(w.club(r.clubId).name, bold = r.clubId == club.id) }
                        SmallText("${r.played}  ${r.gd}  ${r.points}", bold = r.clubId == club.id)
                    }
                }
            }
        }
        item {
            SectionCard(vm.t("Kotak Masuk", "Inbox")) {
                for (m in w.inbox.takeLast(5).reversed()) {
                    Text("• " + c.message(m), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
                TextButton(onClick = { vm.navigate(Screen.Inbox) }) { Text(vm.t("Semua pesan", "All messages")) }
            }
        }
        item {
            SectionCard(vm.t("Berita Sepak Bola", "Football News")) {
                for (m in w.news.takeLast(5).reversed()) Text("• " + c.message(m), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                if (w.news.isEmpty()) SmallText("-")
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
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
        Text(a.name, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold)
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
    val squad = w.squad(club.id)
    val ref = squad.map { it.ca }.sortedDescending().take(11).average().takeIf { !it.isNaN() } ?: 100.0
    val cols = listOf<Col<Player>>(
        Col(vm.t("Pos", "Pos"), 44.dp, { it.position.ordinal * -1 }) { SmallText(it.position.label(vm.lang), bold = true) },
        Col(vm.t("Nama", "Name"), androidx.compose.ui.unit.Dp.Unspecified, { it.lastName }) {
            Column {
                SmallText(it.name, bold = true)
                val status = when { it.injuryDays > 0 -> vm.t("Cedera", "Injured") + " ${it.injuryDays}h"; it.suspended > 0 -> vm.t("Skorsing", "Suspended"); it.transferListed -> vm.t("Dijual", "Listed"); it.loanClubId >= 0 && it.clubId != club.id -> vm.t("Pinjaman", "On loan"); else -> "" }
                if (status.isNotEmpty()) SmallText(status, MaterialTheme.colorScheme.error)
            }
        },
        Col(vm.t("Usia", "Age"), 36.dp, { it.age(w.today) }) { SmallText("${it.age(w.today)}") },
        Col(vm.t("Kmp", "Abl"), 72.dp, { it.ca }) { Stars(Ability.starsFor(it.ca, ref), 11.dp) },
        Col(vm.t("Kond", "Cond"), 44.dp, { it.condition }) { SmallText("${it.condition}%", if (it.condition < 75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        Col(vm.t("Moral", "Mor"), 40.dp, { it.morale }) { SmallText(moraleLabel(it.morale)) },
    )
    SortableTable(squad, cols, key = { it.id }, onClick = { vm.navigate(Screen.PlayerDetail(it.id)) }, defaultSort = 0)
}

fun moraleLabel(m: Int) = when { m >= 85 -> "😄"; m >= 65 -> "🙂"; m >= 45 -> "😐"; m >= 25 -> "🙁"; else -> "😠" }

object Ability {
    fun starsFor(ca: Int, ref: Double) = fm.engine.ability.Ability.stars(ca, ref)
}
