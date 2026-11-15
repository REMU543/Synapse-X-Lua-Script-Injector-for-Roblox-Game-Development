package fm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fm.app.GameViewModel
import fm.core.*
import fm.engine.game.Career

private val editorPalette = listOf(0xFFD32F2F, 0xFFC62828, 0xFF1976D2, 0xFF0D47A1, 0xFF388E3C, 0xFF1B5E20, 0xFFFBC02D, 0xFFF57C00,
    0xFF7B1FA2, 0xFF0097A7, 0xFFC2185B, 0xFF5D4037, 0xFF455A64, 0xFF212121, 0xFFFFFFFF, 0xFF8BC34A)

/** In-game database editor: clubs (name, colours, stadium, reputation) and player names. */
@Composable
fun EditorScreen(vm: GameViewModel) {
    vm.version // recompose when the world changes
    val c = vm.career ?: return
    val w = c.world
    val leagues = w.competitions.filter { it.type == CompType.LEAGUE }
    var league by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(w.userClub?.id ?: w.clubs.keys.first()) }
    var editPlayer by remember { mutableStateOf<Player?>(null) }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(280.dp).fillMaxHeight()) {
            ChipRow(leagues.map { it.shortName }, league) { league = it }
            LazyColumn {
                items(w.clubs.values.filter { it.leagueId == leagues[league].id }.sortedBy { it.name }, key = { it.id }) { cl ->
                    Row(Modifier.fillMaxWidth().clickable { selected = cl.id }
                        .background(if (cl.id == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ClubBadge(cl, 24.dp); Spacer(Modifier.width(8.dp)); SmallText(cl.name)
                    }
                }
            }
        }
        val club = w.clubs[selected] ?: return@Row
        key(club.id, vm.version) {
            var name by remember { mutableStateOf(club.name) }
            var short by remember { mutableStateOf(club.shortName) }
            var city by remember { mutableStateOf(club.city) }
            var stadium by remember { mutableStateOf(club.stadiumName) }
            var rep by remember { mutableFloatStateOf(club.reputation.toFloat()) }
            var cap by remember { mutableStateOf(club.stadiumCapacity.toString()) }
            var c1 by remember { mutableStateOf(club.primaryColor) }
            var c2 by remember { mutableStateOf(club.secondaryColor) }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Panel(vm.t("Edit Klub", "Edit Club")) {
                    Row {
                        OutlinedTextField(name, { name = it }, label = { Text(vm.t("Nama", "Name")) }, singleLine = true, modifier = Modifier.weight(2f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(short, { short = it.take(4) }, label = { Text(vm.t("Singkatan", "Short")) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row {
                        OutlinedTextField(city, { city = it }, label = { Text(vm.t("Kota", "City")) }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(stadium, { stadium = it }, label = { Text(vm.t("Stadion", "Stadium")) }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(cap, { cap = it.filter(Char::isDigit).take(6) }, label = { Text(vm.t("Kapasitas", "Capacity")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(110.dp))
                    }
                    Text(vm.t("Reputasi: ", "Reputation: ") + rep.toInt(), style = MaterialTheme.typography.labelLarge)
                    Slider(rep, { rep = it }, valueRange = 1f..100f)
                    for ((label, value, set) in listOf(Triple(vm.t("Warna utama", "Primary colour"), c1) { v: Long -> c1 = v },
                        Triple(vm.t("Warna kedua", "Secondary colour"), c2) { v: Long -> c2 = v })) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.padding(vertical = 4.dp)) {
                            for (col in editorPalette) Box(Modifier.padding(2.dp).size(24.dp).clip(CircleShape).background(Color(col))
                                .border(if (col == value) 3.dp else 1.dp, if (col == value) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                                .clickable { set(col) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            val e = Career.ClubEdit(name, short, city, stadium, rep.toInt(), c1, c2, cap.toIntOrNull() ?: 0)
                            vm.act { cc -> val probs = cc.editClub(club.id, e); vm.toast = if (probs.isEmpty()) I18n.t(vm.lang, "editor_saved") else probs.joinToString() }
                        }) { Text(vm.t("Simpan", "Save")) }
                        Spacer(Modifier.width(10.dp))
                        ClubBadge(club.copyForPreview(c1, c2, short), 36.dp)
                    }
                }
                Panel(vm.t("Pemain", "Players")) {
                    for (p in w.squad(club.id).sortedBy { it.position.ordinal }) Row(Modifier.fillMaxWidth().clickable { editPlayer = p }.padding(vertical = 4.dp)) {
                        Box(Modifier.width(40.dp)) { SmallText(p.position.label(vm.lang), bold = true) }
                        Box(Modifier.weight(1f)) { SmallText(p.name) }
                        SmallText(vm.t("Ubah nama", "Rename"), MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    editPlayer?.let { p ->
        var first by remember(p.id) { mutableStateOf(p.firstName) }
        var last by remember(p.id) { mutableStateOf(p.lastName) }
        AlertDialog(onDismissRequest = { editPlayer = null }, title = { Text(vm.t("Ubah nama pemain", "Rename player")) },
            text = { Column {
                OutlinedTextField(first, { first = it }, label = { Text(vm.t("Nama depan", "First name")) }, singleLine = true)
                OutlinedTextField(last, { last = it }, label = { Text(vm.t("Nama belakang", "Last name")) }, singleLine = true)
            } },
            confirmButton = { TextButton(onClick = { val f = first; val l = last; vm.act { it.editPlayerName(p.id, f, l) }; editPlayer = null }) { Text(vm.t("Simpan", "Save")) } },
            dismissButton = { TextButton(onClick = { editPlayer = null }) { Text(vm.t("Batal", "Cancel")) } })
    }
}

/** A detached copy of the club used only for the colour preview badge. */
private fun Club.copyForPreview(c1: Long, c2: Long, short: String) = Club(id, nationId, name, short.ifBlank { shortName }, city, reputation, stadiumName,
    stadiumCapacity, trainingFacilities, youthFacilities, youthRecruitment, c1, c2, founded, balance, transferBudget, wageBudget, leagueId)

/** Backup / restore controls (Settings and start screen). */
@Composable
fun BackupControls(vm: GameViewModel, canBackup: Boolean) {
    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.backupSave(uri)
    }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.checkRestore(uri)
    }
    if (canBackup) OutlinedButton(onClick = { backupLauncher.launch("${vm.slot}.fmsave") }, Modifier.fillMaxWidth()) { Text(vm.t("Cadangkan ke file / cloud", "Back up to file / cloud")) }
    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text(vm.t("Pulihkan dari cadangan", "Restore from backup")) }
    SmallText(vm.t("Pilih folder Google Drive atau penyimpanan lain di pemilih file Android. Tidak memakai layanan berbayar.",
        "Pick a Google Drive folder or other storage in the Android file picker. No paid services."), MaterialTheme.colorScheme.onSurfaceVariant)
    vm.pendingRestore?.let { p ->
        AlertDialog(onDismissRequest = { vm.pendingRestore = null },
            title = { Text(vm.t("Simpanan lokal lebih baru", "Local save is newer")) },
            text = {
                Text(vm.t("Cadangan: ", "Backup: ") + Cal.format(p.check.info.day, vm.lang) + "\n" +
                    vm.t("Lokal: ", "Local: ") + (p.check.localInfo?.let { Cal.format(it.day, vm.lang) } ?: "-") + "\n\n" +
                    vm.t("Simpanan lokal tidak akan ditimpa tanpa persetujuan Anda.", "The local save is never overwritten without your consent."))
            },
            confirmButton = { TextButton(onClick = { vm.restore(p, asCopy = true, overwrite = false) }) { Text(vm.t("Pulihkan sebagai salinan", "Restore as a copy")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.pendingRestore = null }) { Text(vm.t("Batal", "Cancel")) }
                    TextButton(onClick = { vm.restore(p, asCopy = false, overwrite = true) }) { Text(vm.t("Timpa yang lokal", "Overwrite local"), color = LocalFm.current.bad) }
                }
            })
    }
}
