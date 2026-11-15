package fm.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fm.core.*
import fm.engine.game.Career
import fm.engine.match.MatchEngine2D
import fm.engine.save.SaveStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Navigation destinations (simple state-based navigation, no extra library). */
sealed class Screen {
    data object Start : Screen()
    data object NewCareer : Screen()
    data object Home : Screen()
    data object Squad : Screen()
    data object Tactics : Screen()
    data object Transfers : Screen()
    data object Competitions : Screen()
    data object Training : Screen()
    data object Scouting : Screen()
    data object Club : Screen()
    data object Finances : Screen()
    data object Inbox : Screen()
    data object Profile : Screen()
    data object Settings : Screen()
    data object Match : Screen()
    data object Dynamics : Screen()
    data object Staff : Screen()
    data object International : Screen()
    data object DataHub : Screen()
    data object Editor : Screen()
    data object MatchDayFlow : Screen()
    data class PlayerDetail(val id: Long) : Screen()
    data class ClubDetail(val id: Long) : Screen()
}

/**
 * Owns the Career. All world mutations run on a background dispatcher under a mutex; while one
 * runs, [busy] is true and the UI shows a progress overlay instead of reading the world, so the
 * UI never observes a half-updated world.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    val store = SaveStore(File(app.filesDir, "saves"))
    private val mutex = Mutex()

    var career: Career? by mutableStateOf(null); private set
    var screen: Screen by mutableStateOf(Screen.Start)
    private val backStack = ArrayDeque<Screen>()
    var busy by mutableStateOf(false); private set
    var version by mutableIntStateOf(0); private set
    var toast: String? by mutableStateOf(null)
    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    var theme by mutableStateOf(runCatching { fm.app.ui.ThemeChoice.valueOf(prefs.getString("theme", "ARANG")!!) }.getOrDefault(fm.app.ui.ThemeChoice.ARANG))
        private set
    fun setThemeChoice(t: fm.app.ui.ThemeChoice) { theme = t; prefs.edit().putString("theme", t.name).apply() }

    // ---------------------------------------------------------------- match day flow
    enum class MdStage { PRESS_PRE, PREVIEW, TALK_PRE, LIVE, REPORT, TALK_FULL, PRESS_POST }
    var matchDay: fm.engine.game.MatchDay? by mutableStateOf(null); private set
    var mdStage by mutableStateOf(MdStage.PREVIEW)
    var talkResult: List<fm.engine.dynamics.TeamTalk.Reaction> by mutableStateOf(emptyList())
    var pressReaction: String? by mutableStateOf(null)

    fun beginMatchDay() {
        val c = career ?: return
        val f = c.userFixtureToday() ?: return
        val md = fm.engine.game.MatchDay(c, f)
        matchDay = md
        md.pressPre()
        talkResult = emptyList(); pressReaction = null
        mdStage = MdStage.PRESS_PRE
        navigate(Screen.MatchDayFlow)
    }

    fun answerPress(q: PressQuestion, a: fm.engine.dynamics.Press.Answer) {
        val w = world ?: return
        pressReaction = fm.core.I18n.t(lang, fm.engine.dynamics.Press.answer(w, q, a))
        version++
    }

    fun skipPress() { world?.pendingPress = null; pressReaction = null; mdStage = if (mdStage == MdStage.PRESS_PRE) MdStage.PREVIEW else MdStage.PRESS_POST; version++ }

    fun teamTalk(stage: fm.engine.dynamics.TeamTalk.Stage, tone: fm.engine.dynamics.TeamTalk.Tone) {
        val md = matchDay ?: return
        talkResult = md.talk(stage, tone)
        version++
    }

    fun kickOff() {
        val md = matchDay ?: return
        engine = md.start()
        talkResult = emptyList()
        mdStage = MdStage.LIVE
    }

    fun finishLiveMatch() = work {
        val md = matchDay ?: return@work
        md.finish()
        engine = null
        talkResult = emptyList()
        mdStage = MdStage.REPORT
    }

    fun endMatchDay() = work {
        val c = career ?: return@work
        c.world.pendingPress = null
        matchDay = null
        c.advanceDay()
        autosave()
        withContext(Dispatchers.Main) { navigate(Screen.Home, root = true) }
    }
    var slot by mutableStateOf("karier1")
    var engine: MatchEngine2D? by mutableStateOf(null); private set

    val world: World? get() = career?.world
    val lang: Lang get() = career?.world?.settings?.lang ?: uiLang
    var uiLang by mutableStateOf(runCatching { Lang.valueOf(prefs.getString("lang", "ID")!!) }.getOrDefault(Lang.ID))

    fun t(id: String, en: String) = if (lang == Lang.ID) id else en

    fun navigate(s: Screen, root: Boolean = false) {
        if (root) backStack.clear() else backStack.addLast(screen)
        screen = s
    }

    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        screen = prev
        return true
    }

    fun refresh() { version++ }

    private fun work(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            mutex.withLock {
                busy = true
                try { withContext(Dispatchers.Default) { block() } }
                catch (e: Exception) { toast = "Error: ${e.message}" }
                finally { busy = false; version++ }
            }
        }
    }

    fun slots(): List<SaveStore.SlotInfo> = store.list()

    // ---------------------------------------------------------------- database packs
    var pendingPack: fm.engine.data.DatabasePack? by mutableStateOf(null)
    var packIssues: List<fm.engine.data.Packs.Issue> by mutableStateOf(emptyList())

    /** Parses and validates a JSON pack or a CSV of players picked by the user. */
    fun importPack(uri: android.net.Uri) {
        try {
            val app = getApplication<Application>()
            val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val pack = if (text.trimStart().startsWith("{")) fm.engine.data.Packs.parseJson(text)
                else fm.engine.data.Packs.parsePlayersCsv(text, uri.lastPathSegment ?: "CSV")
            val issues = fm.engine.data.Packs.validate(pack, previewWorld(1))
            packIssues = issues
            pendingPack = if (issues.any { it.error }) null else pack
            toast = if (issues.any { it.error }) t("Database tidak valid: ", "Invalid database: ") + issues.count { it.error } + t(" kesalahan", " errors")
                else t("Database dimuat: ", "Database loaded: ") + "${pack.clubs.size} " + t("klub", "clubs") + ", ${pack.players.size} " + t("pemain", "players")
        } catch (e: Exception) {
            packIssues = listOf(fm.engine.data.Packs.Issue(true, e.message ?: "parse error")); pendingPack = null
            toast = t("Gagal membaca file.", "Could not read the file.")
        }
    }

    fun clearPack() { pendingPack = null; packIssues = emptyList() }

    // ---------------------------------------------------------------- save backup (external file / cloud drive)
    class PendingRestore(val bytes: ByteArray, val check: fm.engine.save.SaveStore.ImportCheck)
    var pendingRestore: PendingRestore? by mutableStateOf(null)

    /** Saves the current career and writes it to a user-chosen file (e.g. a cloud drive folder). */
    fun backupSave(uri: android.net.Uri) = work {
        val c = career ?: return@work
        store.save(c.world, slot)
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(store.exportBytes(slot)) }
        toast = t("Cadangan tersimpan.", "Backup written.")
    }

    /** Reads and verifies a backup file; asks for confirmation if a newer local save exists. */
    fun checkRestore(uri: android.net.Uri) {
        try {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            val check = store.inspect(bytes)
            if (check.localIsNewer) pendingRestore = PendingRestore(bytes, check)
            else restore(PendingRestore(bytes, check), asCopy = false, overwrite = false)
        } catch (e: Exception) { toast = t("Berkas cadangan rusak atau tidak valid.", "The backup file is damaged or invalid.") }
    }

    fun restore(p: PendingRestore, asCopy: Boolean, overwrite: Boolean) {
        pendingRestore = null
        val target = if (asCopy) p.check.info.slot + "_cadangan" else p.check.info.slot
        try {
            store.importBytes(p.bytes, target, overwriteNewer = overwrite)
            load(target)
        } catch (e: Exception) { toast = e.message }
    }

    fun exportPack(uri: android.net.Uri) = work {
        val w = world ?: return@work
        val text = fm.engine.data.Packs.toJson(fm.engine.data.Packs.export(w, "Export ${w.season}"))
        getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
        toast = t("Database diekspor.", "Database exported.")
    }

    fun newCareer(first: String, last: String, nationId: Int, clubId: Long?, seed: Long, style: ManagerStyle) = work {
        val profile = ManagerProfile(first, last, nationId, Cal.of(1988, 1, 1), t("Lisensi B AFC", "AFC B Licence"),
            t("Mantan pemain amatir", "Former amateur player"), style, reputation = 35)
        val c = Career.newCareer(seed, profile, clubId, pendingPack)
        c.world.settings.lang = uiLang
        career = c
        slot = "karier_${seed % 100000}"
        store.save(c.world, slot)
        withContext(Dispatchers.Main) { navigate(Screen.Home, root = true) }
    }

    /** Preview world used by the new-career screen to list clubs (same seed => same world). */
    fun previewWorld(seed: Long, withPack: Boolean = false): World = fm.engine.world.WorldGenerator.generate(seed,
        ManagerProfile("", "", 1, Cal.of(1988, 1, 1), "", "", ManagerStyle("4-4-2", 0.0, 0.5, 0.5, 0.5, 0.5), 30)).also { w ->
        if (withPack) pendingPack?.let { fm.engine.data.Packs.apply(it, w) }
    }

    fun load(slotName: String) = work {
        val (w, from) = store.loadWithRecovery(slotName)
        career = Career(w)
        slot = slotName
        if (from != "$slotName.fmsave") toast = t("Simpanan utama rusak; dipulihkan dari cadangan.", "Main save was damaged; restored from backup.")
        withContext(Dispatchers.Main) { navigate(Screen.Home, root = true) }
    }

    fun deleteSlot(slotName: String) { store.delete(slotName); version++ }

    fun save(manual: Boolean = true) = work {
        val c = career ?: return@work
        store.save(c.world, slot)
        if (manual) toast = t("Permainan disimpan.", "Game saved.")
    }

    private fun autosave() { val c = career ?: return; if (c.world.settings.autosave) store.save(c.world, slot) }

    /** Advance to the next event (match day, important message, ...). */
    fun continueGame() = work {
        val c = career ?: return@work
        if (c.userFixtureToday() != null) return@work
        c.continueGame()
        autosave()
    }

    fun playInstant() = work {
        val c = career ?: return@work
        c.playUserMatchInstant()
        c.advanceDay()
        autosave()
    }

    fun startMatch() {
        val c = career ?: return
        engine = c.prepareUserMatch()
        if (engine != null) navigate(Screen.Match)
    }

    fun finishMatch() = work {
        val c = career ?: return@work
        val e = engine ?: return@work
        c.completeUserMatch(e)
        c.advanceDay()
        engine = null
        autosave()
        withContext(Dispatchers.Main) { navigate(Screen.Home, root = true) }
    }

    /** Runs a world-mutating user action (tactics, transfers, ...) safely. */
    fun act(block: (Career) -> Unit) = work { career?.let(block) }

    fun setLang(l: Lang) { uiLang = l; prefs.edit().putString("lang", l.name).apply(); career?.world?.settings?.lang = l; version++ }
}
