package fm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.app.GameViewModel
import fm.app.Screen
import fm.core.*
import fm.engine.game.DataHub
import fm.engine.international.International

// ---------------------------------------------------------------- international

@Composable
fun InternationalScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    International.ensureTeams(w)
    val nations = w.nations
    var sel by remember { mutableIntStateOf(nations.indexOfFirst { it.id == w.manager.nationId }.coerceAtLeast(0)) }
    val nation = nations[sel]
    val team = w.nationalTeams[nation.id]
    Column(Modifier.fillMaxSize()) {
        ChipRow(nations.map { it.name(vm.lang) }, sel) { sel = it }
        LazyVerticalGrid(GridCells.Adaptive(340.dp), Modifier.fillMaxSize().padding(6.dp)) {
            item {
                Panel(nation.name(vm.lang)) {
                    KeyValue(vm.t("Pelatih", "Manager"), team?.managerName ?: "-")
                    KeyValue(vm.t("Reputasi", "Reputation"), "${nation.reputation}")
                    KeyValue(vm.t("Kekuatan skuad", "Squad strength"), "%.0f".format(International.strength(w, nation.id)))
                    if (team != null) {
                        KeyValue(vm.t("Main (M-S-K)", "Played (W-D-L)"), "${team.played} (${team.won}-${team.drawn}-${team.lost})")
                        KeyValue(vm.t("Gol", "Goals"), "${team.goalsFor} : ${team.goalsAgainst}")
                        KeyValue(vm.t("Gelar", "Titles"), if (team.titles.isEmpty()) "-" else team.titles.joinToString())
                    }
                }
            }
            item {
                Panel(vm.t("Hasil Terakhir", "Recent Results")) {
                    val res = team?.results?.takeLast(8)?.reversed().orEmpty()
                    if (res.isEmpty()) SmallText(vm.t("Belum ada laga.", "No matches yet."))
                    for (r in res) {
                        val (day, opp, gf, ga, kind) = r.split("|")
                        val color = when { gf.toInt() > ga.toInt() -> Pal.win; gf == ga -> Pal.draw; else -> Pal.loss }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            SmallText(Cal.formatShort(day.toInt(), vm.lang), MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) { SmallText("vs " + w.nation(opp.toInt()).name(vm.lang) + "  (" + stageLabel(vm, kind) + ")") }
                            SmallText("$gf-$ga", color, bold = true)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel(vm.t("Skuad Terpanggil", "Current Squad") + " (${team?.squad?.size ?: 0})") {
                    val squad = team?.squad?.mapNotNull { w.players[it] }?.sortedBy { it.position.ordinal }.orEmpty()
                    if (squad.isEmpty()) SmallText(vm.t("Skuad dipanggil menjelang jeda internasional.", "The squad is named before each international window."))
                    for (p in squad) Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.PlayerDetail(p.id)) }.padding(vertical = 2.dp)) {
                        Box(Modifier.width(40.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
                        Box(Modifier.weight(1f)) { SmallText(p.name, bold = p.playingFor == w.manager.clubId) }
                        Box(Modifier.weight(1f)) { SmallText(w.clubs[p.playingFor]?.name ?: vm.t("Bebas", "Free"), MaterialTheme.colorScheme.onSurfaceVariant) }
                        SmallText("${p.internationalCaps} " + vm.t("caps", "caps") + " · ${p.internationalGoals} " + vm.t("gol", "gls"))
                    }
                }
            }
            val t = w.tournament
            if (t != null) {
                for (g in 0..1) item {
                    Panel(t.name(vm.lang) + " — " + vm.t("Grup ", "Group ") + ('A' + g)) {
                        for ((i, r) in International.groupTable(w, g).withIndex()) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Box(Modifier.width(20.dp)) { SmallText("${i + 1}", bold = i < 2) }
                            Box(Modifier.weight(1f)) { SmallText(w.nation(r.nation).name(vm.lang), bold = i < 2) }
                            SmallText("${r.p}  ${r.gd}  ${r.pts}", bold = true)
                        }
                    }
                }
                item {
                    Panel(vm.t("Fase Gugur", "Knockout")) {
                        val ko = w.intlFixtures.filter { (it.stage == "SF" || it.stage == "F1") && Cal.year(it.day) == t.year }
                        if (ko.isEmpty()) SmallText(vm.t("Menunggu fase grup selesai.", "Waiting for the group stage."))
                        for (f in ko) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Box(Modifier.width(56.dp)) { SmallText(stageLabel(vm, f.stage), MaterialTheme.colorScheme.onSurfaceVariant) }
                            Box(Modifier.weight(1f)) { SmallText(w.nation(f.home).name(vm.lang) + " vs " + w.nation(f.away).name(vm.lang)) }
                            SmallText(if (f.played) "${f.homeGoals}-${f.awayGoals}" + (if (f.penHome >= 0) " (p ${f.penHome}-${f.penAway})" else "") else Cal.formatShort(f.day, vm.lang), bold = f.played)
                        }
                        if (t.winner >= 0) Text("🏆 " + w.nation(t.winner).name(vm.lang), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Panel(vm.t("Juara Turnamen", "Tournament Winners")) {
                    if (w.tournamentHistory.isEmpty()) SmallText("-")
                    for (h in w.tournamentHistory.reversed()) { val (y, win, ru) = h.split("|")
                        KeyValue(y, w.nation(win.toInt()).name(vm.lang) + vm.t(" (final vs ", " (final vs ") + w.nation(ru.toInt()).name(vm.lang) + ")") }
                }
            }
        }
    }
}

private fun stageLabel(vm: GameViewModel, s: String) = when (s) {
    "F" -> vm.t("Persahabatan", "Friendly"); "G" -> vm.t("Grup", "Group"); "SF" -> vm.t("Semifinal", "Semi-final"); "F1" -> vm.t("Final", "Final"); else -> s
}

// ---------------------------------------------------------------- data hub

@Composable
fun DataHubScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    val club = w.userClub ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ChipRow(listOf(vm.t("Performa Tim", "Team Performance"), vm.t("Statistik Pemain", "Player Stats"), vm.t("Papan Peringkat Liga", "League Leaders")), tab) { tab = it }
        when (tab) {
            0 -> LazyVerticalGrid(GridCells.Adaptive(360.dp), Modifier.fillMaxSize().padding(6.dp)) {
                val own = DataHub.averages(w, club.id, club.leagueId)
                val lg = DataHub.leagueAverages(w, club.leagueId)
                item {
                    Panel(vm.t("Rata-rata per Laga vs Liga", "Per-Match Averages vs League")) {
                        if (own == null || lg == null) SmallText(vm.t("Data tersedia setelah beberapa laga liga.", "Available after a few league matches."))
                        else {
                            Row { Box(Modifier.weight(1f)) {}; SmallText(vm.t("Kita", "Us"), bold = true); Spacer(Modifier.width(24.dp)); SmallText(vm.t("Liga", "League"), bold = true) }
                            CompareRow(vm.t("Gol dicetak", "Goals scored"), own.goalsFor, lg.goalsFor, true)
                            CompareRow(vm.t("Gol kebobolan", "Goals conceded"), own.goalsAgainst, lg.goalsAgainst, false)
                            CompareRow("xG", own.xgFor, lg.xgFor, true)
                            CompareRow(vm.t("xG lawan", "xG against"), own.xgAgainst, lg.xgAgainst, false)
                            CompareRow(vm.t("Tembakan", "Shots"), own.shotsFor, lg.shotsFor, true)
                            CompareRow(vm.t("Tembakan lawan", "Shots against"), own.shotsAgainst, lg.shotsAgainst, false)
                            CompareRow(vm.t("Penguasaan bola %", "Possession %"), own.possession, lg.possession, true)
                            CompareRow(vm.t("Akurasi umpan %", "Pass completion %"), own.passPct, lg.passPct, true)
                            CompareRow(vm.t("Sepak pojok", "Corners"), own.corners, lg.corners, true)
                            CompareRow(vm.t("Pelanggaran", "Fouls"), own.fouls, lg.fouls, false)
                        }
                    }
                }
                item {
                    Panel(vm.t("Tren xG (10 laga terakhir)", "xG Trend (last 10 matches)")) {
                        val pts = DataHub.matchPoints(w, club.id).takeLast(10)
                        if (pts.isEmpty()) SmallText("-") else {
                            val good = LocalFm.current.good; val bad = LocalFm.current.bad
                            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                                val max = (pts.maxOf { maxOf(it.xgFor, it.xgAgainst) }).coerceAtLeast(1.0).toFloat()
                                val bw = size.width / pts.size
                                pts.forEachIndexed { i, p ->
                                    val x = i * bw
                                    val hf = (p.xgFor.toFloat() / max) * size.height * 0.45f
                                    val ha = (p.xgAgainst.toFloat() / max) * size.height * 0.45f
                                    val bar = minOf(bw * 0.6f, 36.dp.toPx())
                                    val x0 = x + (bw - bar) / 2
                                    drawRect(good, Offset(x0, size.height / 2 - hf), Size(bar, hf))
                                    drawRect(bad, Offset(x0, size.height / 2), Size(bar, ha))
                                }
                                drawLine(Color.Gray, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                            }
                            Row { for (p in pts) Text("${p.gf}-${p.ga}", Modifier.weight(1f), fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = when { p.gf > p.ga -> Pal.win; p.gf == p.ga -> Pal.draw; else -> Pal.loss }) }
                            SmallText(vm.t("Atas: xG kita · Bawah: xG lawan", "Top: our xG · Bottom: opponents' xG"), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            1 -> {
                val squad = w.squad(club.id)
                val cols = listOf<Col<Player>>(
                    Col(vm.t("Pos", "Pos"), 44.dp, { -it.position.ordinal }) { SmallText(it.position.label(vm.lang), bold = true) },
                    Col(vm.t("Nama", "Name"), Dp.Unspecified, { it.lastName }) { SmallText(it.name, bold = true) },
                    Col(vm.t("Main", "Apps"), 44.dp, { it.season.apps }) { SmallText("${it.season.apps}") },
                    Col(vm.t("Menit", "Mins"), 52.dp, { it.season.minutes }) { SmallText("${it.season.minutes}") },
                    Col(vm.t("Gol", "Gls"), 40.dp, { it.season.goals }) { SmallText("${it.season.goals}") },
                    Col(vm.t("Gol/90", "Gls/90"), 54.dp, { DataHub.per90(it.season.goals, it.season.minutes) }) { SmallText("%.2f".format(DataHub.per90(it.season.goals, it.season.minutes))) },
                    Col(vm.t("Ast", "Ast"), 40.dp, { it.season.assists }) { SmallText("${it.season.assists}") },
                    Col(vm.t("Ast/90", "Ast/90"), 54.dp, { DataHub.per90(it.season.assists, it.season.minutes) }) { SmallText("%.2f".format(DataHub.per90(it.season.assists, it.season.minutes))) },
                    Col(vm.t("Rata", "Rating"), 50.dp, { it.season.avgRating }) { SmallText(if (it.season.apps > 0) "%.2f".format(it.season.avgRating) else "-") },
                    Col(vm.t("KK/KM", "Y/R"), 50.dp, { it.season.yellow * 10 + it.season.red }) { SmallText("${it.season.yellow}/${it.season.red}") },
                    Col(vm.t("Timnas", "Caps"), 50.dp, { it.internationalCaps }) { SmallText("${it.internationalCaps}") },
                )
                SortableTable(squad, cols, key = { it.id }, onClick = { vm.navigate(Screen.PlayerDetail(it.id)) }, defaultSort = 4)
            }
            else -> LazyVerticalGrid(GridCells.Adaptive(300.dp), Modifier.fillMaxSize().padding(6.dp)) {
                for (kind in DataHub.Leader.entries) item {
                    Panel(kind.label(vm.lang) + " — " + w.comp(club.leagueId).shortName) {
                        val list = DataHub.leaders(w, club.leagueId, kind, 10)
                        if (list.isEmpty()) SmallText("-")
                        for ((i, e) in list.withIndex()) Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.PlayerDetail(e.first.id)) }.padding(vertical = 2.dp)) {
                            Box(Modifier.width(22.dp)) { SmallText("${i + 1}") }
                            Box(Modifier.weight(1f)) { SmallText(e.first.name, bold = e.first.playingFor == club.id) }
                            Box(Modifier.width(60.dp)) { SmallText(w.clubs[e.first.playingFor]?.shortName ?: "-", MaterialTheme.colorScheme.onSurfaceVariant) }
                            SmallText(if (kind == DataHub.Leader.RATING) "%.2f".format(e.second) else "${e.second.toInt()}", bold = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, ours: Double, league: Double, higherIsBetter: Boolean) {
    val better = if (higherIsBetter) ours >= league else ours <= league
    val color = if (better) LocalFm.current.good else LocalFm.current.bad
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("%.2f".format(ours), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
        Text("%.2f".format(league), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
    }
}
