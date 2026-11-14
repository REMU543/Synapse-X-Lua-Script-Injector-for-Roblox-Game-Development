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

    fun newCareer(first: String, last: String, nationId: Int, clubId: Long?, seed: Long, style: ManagerStyle) = work {
        val profile = ManagerProfile(first, last, nationId, Cal.of(1988, 1, 1), t("Lisensi B AFC", "AFC B Licence"),
            t("Mantan pemain amatir", "Former amateur player"), style, reputation = 35)
        val c = Career.newCareer(seed, profile, clubId)
        c.world.settings.lang = uiLang
        career = c
        slot = "karier_${seed % 100000}"
        store.save(c.world, slot)
        withContext(Dispatchers.Main) { navigate(Screen.Home, root = true) }
    }

    /** Preview world used by the new-career screen to list clubs (same seed => same world). */
    fun previewWorld(seed: Long): World = fm.engine.world.WorldGenerator.generate(seed,
        ManagerProfile("", "", 1, Cal.of(1988, 1, 1), "", "", ManagerStyle("4-4-2", 0.0, 0.5, 0.5, 0.5, 0.5), 30))

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
