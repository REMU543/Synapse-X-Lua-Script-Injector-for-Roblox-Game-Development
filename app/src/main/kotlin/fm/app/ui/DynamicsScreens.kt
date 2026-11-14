package fm.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fm.app.GameViewModel
import fm.app.Screen
import fm.core.*
import fm.engine.dynamics.Dynamics
import fm.engine.dynamics.Interactions

@Composable
fun DynamicsScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val w = vm.world ?: return
    val club = w.userClub ?: run { Text(vm.t("Anda belum memiliki klub.", "You have no club."), Modifier.padding(16.dp)); return }
    var tab by remember { mutableIntStateOf(0) }
    var talkTo by remember { mutableStateOf<Player?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            Panel(vm.t("Suasana Ruang Ganti", "Dressing Room Atmosphere"), Modifier.weight(1f)) { MeterRow(vm.t("Suasana", "Atmosphere"), w.atmosphere) }
            Panel(vm.t("Kekompakan Tim", "Team Cohesion"), Modifier.weight(1f)) { MeterRow(vm.t("Kekompakan", "Cohesion"), Dynamics.cohesion(w, club.id)) }
            Panel(vm.t("Media", "Media"), Modifier.weight(1f)) { MeterRow(vm.t("Ekspektasi publik", "Media hype"), w.mediaHype) }
        }
        ChipRow(listOf(vm.t("Hierarki", "Hierarchy"), vm.t("Kebahagiaan", "Happiness"), vm.t("Kelompok Sosial", "Social Groups"), vm.t("Janji", "Promises")), tab) { tab = it }
        Spacer(Modifier.height(4.dp))
        when (tab) {
            0 -> {
                val h = Dynamics.hierarchy(w, club.id)
                LazyVerticalGrid(GridCells.Adaptive(300.dp), Modifier.fillMaxSize().padding(6.dp)) {
                    for (tier in Dynamics.Tier.entries) {
                        val members = h.filter { it.tier == tier }
                        if (members.isEmpty()) continue
                        item {
                            Panel(tier.label(vm.lang)) {
                                for (e in members) Row(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.PlayerDetail(e.player.id)) }.padding(vertical = 3.dp)) {
                                    Box(Modifier.width(36.dp)) { SmallText(e.player.position.label(vm.lang), bold = true) }
                                    Box(Modifier.weight(1f)) { SmallText(e.player.name) }
                                    SmallText(Dynamics.happinessLabel(e.player.happiness, vm.lang), happinessColor(e.player.happiness))
                                }
                            }
                        }
                    }
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                items(w.squad(club.id).sortedBy { it.happiness }, key = { it.id }) { p ->
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(40.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                val fs = Dynamics.factors(w, p)
                                SmallText(if (fs.isEmpty()) vm.t("Tidak ada faktor khusus", "No particular factors")
                                    else fs.joinToString(" · ") { (if (it.value > 0) "▲ " else "▼ ") + it.label(vm.lang) }, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Dynamics.happinessLabel(p.happiness, vm.lang), color = happinessColor(p.happiness), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
                            OutlinedButton(onClick = { talkTo = p }, enabled = Interactions.canTalk(w, p)) { Text(vm.t("Bicara", "Talk")) }
                        }
                    }
                }
            }
            2 -> LazyVerticalGrid(GridCells.Adaptive(280.dp), Modifier.fillMaxSize().padding(6.dp)) {
                items(Dynamics.socialGroups(w, club.id, vm.lang)) { (name, members) ->
                    Panel(name) { for (p in members) PlayerLineSmall(vm, p, Dynamics.happinessLabel(p.happiness, vm.lang)) }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(6.dp)) {
                val list = w.promises.reversed()
                if (list.isEmpty()) item { SmallText(vm.t("  Belum ada janji yang dibuat.", "  No promises made yet.")) }
                items(list) { pr ->
                    val p = w.players[pr.playerId]
                    Panel {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(p?.name ?: "?", fontWeight = FontWeight.SemiBold)
                                SmallText(pr.type.label(vm.lang) + " · " + vm.t("batas ", "due ") + Cal.formatShort(pr.deadlineDay, vm.lang))
                            }
                            val (label, color) = when {
                                !pr.resolved -> vm.t("Berjalan", "Ongoing") to MaterialTheme.colorScheme.secondary
                                pr.kept -> vm.t("Ditepati", "Kept") to LocalFm.current.good
                                else -> vm.t("Dilanggar", "Broken") to LocalFm.current.bad
                            }
                            Text(label, color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    talkTo?.let { p -> InteractionDialog(vm, p) { talkTo = null } }
}

@Composable
fun happinessColor(h: Int) = when { h >= 62 -> LocalFm.current.good; h >= 45 -> LocalFm.current.warn; else -> LocalFm.current.bad }

/** Private conversation with a player: topic → response → optional reply. */
@Composable
fun InteractionDialog(vm: GameViewModel, p: Player, onClose: () -> Unit) {
    val w = vm.world ?: return
    var outcome by remember { mutableStateOf<Interactions.Outcome?>(null) }
    var final by remember { mutableStateOf<Interactions.Outcome?>(null) }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(vm.t("Bicara dengan ", "Talk to ") + p.name) },
        text = {
            Column(Modifier.widthIn(min = 420.dp)) {
                Row {
                    SmallText(vm.t("Hubungan: ", "Relationship: ") + "${p.relationship}/100   ")
                    SmallText(vm.t("Kebahagiaan: ", "Happiness: ") + Dynamics.happinessLabel(p.happiness, vm.lang), happinessColor(p.happiness))
                }
                Spacer(Modifier.height(8.dp))
                val o = outcome
                if (o == null) {
                    for (t in Interactions.available(w, p)) OutlinedButton(onClick = {
                        vm.act { outcome = Interactions.talk(it.world, p, t) }
                    }, Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(t.label(vm.lang)) }
                } else {
                    Text(I18n.t(vm.lang, o.responseKey, o.args), style = MaterialTheme.typography.bodyLarge,
                        color = if (o.positive) LocalFm.current.good else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    val f = final
                    if (f != null) Text(I18n.t(vm.lang, f.responseKey, f.args), color = if (f.positive) LocalFm.current.good else LocalFm.current.bad)
                    else for (r in o.followUps) OutlinedButton(onClick = { vm.act { final = Interactions.reply(it.world, p, r) } },
                        Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(r.label(vm.lang)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(vm.t("Selesai", "Done")) } })
}
