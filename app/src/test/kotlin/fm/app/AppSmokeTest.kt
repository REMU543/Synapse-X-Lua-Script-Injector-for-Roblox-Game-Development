package fm.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fm.core.ManagerStyle
import fm.engine.dynamics.Press
import fm.engine.dynamics.TeamTalk
import fm.engine.world.WorldGenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Runs the real Activity and Compose UI on the JVM (Robolectric) in landscape: start screen,
 * new career, every main screen, the full match day (press, briefing, team talks, live match,
 * report) and save/load. Screenshots are written to build/screenshots for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        try {
            rule.activityRule.scenario.onActivity { act ->
                val v = act.window.decorView
                val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } catch (e: Throwable) { println("SHOT FAILED $name: $e") }
    }

    private fun vm(): GameViewModel {
        var v: GameViewModel? = null
        rule.activityRule.scenario.onActivity { v = androidx.lifecycle.ViewModelProvider(it)[GameViewModel::class.java] }
        return v!!
    }

    private fun idleLooper() = rule.runOnUiThread { shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16)) }

    /** Pumps the main looper while background work runs (coroutines resume on Main). */
    private fun pumpUntil(timeoutMs: Long = 120_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timeout")
            idleLooper(); Thread.sleep(20)
        }
        repeat(5) { idleLooper() }
        rule.waitForIdle()
    }

    /** Scrolls whichever scrollable container holds the node (grids compose lazily). */
    private fun scrollTo(m: SemanticsMatcher) {
        val n = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until n) if (runCatching { rule.onAllNodes(hasScrollAction())[i].performScrollToNode(m) }.isSuccess) { rule.waitForIdle(); return }
    }

    private fun waitIdle(vm: GameViewModel) = pumpUntil { !vm.busy }

    private fun go(vm: GameViewModel, s: Screen, name: String) { rule.runOnUiThread { vm.navigate(s, root = true) }; rule.waitForIdle(); shot(name) }

    @Test
    fun fullUiFlowLandscape() {
        rule.onNodeWithText("Karier Baru").assertExists()
        shot("01_start")
        val vm = vm()
        rule.onNodeWithText("Karier Baru").performClick()
        rule.waitForIdle()
        shot("02_new_career")
        val club = vm.previewWorld(123).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        rule.runOnUiThread { vm.newCareer("Uji", "Coba", 1, club, 123, ManagerStyle("4-3-3", 0.2, 0.5, 0.5, 0.5, 0.5)) }
        pumpUntil { (vm.career != null && !vm.busy) || vm.toast != null }
        assertNull("no error: " + vm.toast, vm.toast)
        assertEquals(Screen.Home, vm.screen)
        shot("03_home")
        val screens = listOf(Screen.Squad to "04_squad", Screen.Dynamics to "05_dynamics", Screen.Tactics to "06_tactics", Screen.Training to "07_training",
            Screen.Competitions to "08_competitions", Screen.Scouting to "09_scouting", Screen.Transfers to "10_transfers", Screen.Club to "11_club",
            Screen.Finances to "12_finances", Screen.Inbox to "13_inbox", Screen.Profile to "14_profile", Screen.Settings to "15_settings")
        for ((s, n) in screens) go(vm, s, n)
        // Tactics: slot dialog (role & duty, instructions) and set pieces
        go(vm, Screen.Tactics, "06a_tactics")
        rule.onNodeWithText("Peran Pemain").performClick(); rule.waitForIdle()
        shot("06b_tactics_roles")
        rule.onAllNodes(hasText("—", substring = true))[1].performClick(); rule.waitForIdle()
        rule.onNodeWithText("Peran & Tugas").performClick(); rule.waitForIdle()
        shot("06c_slot_role")
        rule.onNodeWithText("Instruksi").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Lebih sering menembak").performClick(); rule.waitForIdle()
        rule.onNodeWithText("OK").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Bola Mati").performClick(); rule.waitForIdle()
        shot("06d_set_pieces")
        rule.onNodeWithText("Simpan").performClick(); waitIdle(vm)
        assertTrue("player instruction saved", vm.world!!.userClub!!.tactic.effectiveSlots().any { it.instructions.shootMore })
        // Training schedule & staff market
        go(vm, Screen.Training, "07a_training")
        go(vm, Screen.Staff, "11a_staff")
        rule.onNodeWithText("Bursa Staf").performClick(); rule.waitForIdle()
        shot("11b_staff_market")
        val before = vm.world!!.userClub!!.staffIds.size
        rule.onAllNodesWithText("Rekrut")[0].performClick(); waitIdle(vm)
        assertTrue("staff hired or refused with a message", vm.world!!.userClub!!.staffIds.size > before || vm.toast != null)
        vm.toast = null
        val pid = vm.world!!.squad(club).maxBy { it.ca }.id
        go(vm, Screen.PlayerDetail(pid), "16_player")
        // Private chat from the player profile
        scrollTo(hasText("Bicara dengan pemain"))
        rule.onNodeWithText("Bicara dengan pemain").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Puji latihannya").performClick(); waitIdle(vm)
        shot("17_chat")
        rule.onNodeWithText("Selesai").performClick(); rule.waitForIdle()
        go(vm, Screen.Home, "18_home")
        // Advance to the first match day
        var guard = 0
        while (vm.career!!.userFixtureToday() == null && guard++ < 20) { rule.runOnUiThread { vm.continueGame() }; waitIdle(vm) }
        assertNotNull("reached a match day", vm.career!!.userFixtureToday())
        rule.onNodeWithText("Menuju Pertandingan").performClick(); rule.waitForIdle()
        assertEquals(Screen.MatchDayFlow, vm.screen)
        assertEquals(GameViewModel.MdStage.PRESS_PRE, vm.mdStage)
        shot("19_press")
        // Answer all press questions
        var q = 0
        while (vm.world!!.pendingPress != null && q++ < 5) { rule.onNodeWithText(Press.Answer.CAUTIOUS.id).performClick(); rule.waitForIdle() }
        rule.onNodeWithText("Lanjut").performClick(); rule.waitForIdle()
        assertEquals(GameViewModel.MdStage.PREVIEW, vm.mdStage)
        shot("20_preview")
        rule.onNodeWithText("Ke ruang ganti (Team Talk)").performClick(); rule.waitForIdle()
        rule.onNodeWithText(TeamTalk.Tone.CALM.id).performClick(); rule.waitForIdle()
        shot("21_teamtalk")
        assertTrue(vm.talkResult.isNotEmpty())
        // Live match: drive frames manually
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Lanjut").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(GameViewModel.MdStage.LIVE, vm.mdStage)
        val engine = vm.engine!!
        repeat(240) { idleLooper(); rule.mainClock.advanceTimeByFrame() }
        assertTrue("engine advanced during frames", engine.tick > 0)
        shot("22_live")
        rule.onNodeWithText("Statistik").performClick(); rule.mainClock.advanceTimeBy(100); shot("23_live_stats")
        rule.onNodeWithText("Asisten").performClick(); rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Taktik").performClick(); rule.mainClock.advanceTimeBy(100); shot("23b_live_tactics")
        rule.runOnUiThread { vm.finishLiveMatch() }
        val end = System.currentTimeMillis() + 120_000
        while ((vm.busy || vm.mdStage == GameViewModel.MdStage.LIVE) && System.currentTimeMillis() < end) {
            idleLooper(); rule.mainClock.advanceTimeByFrame(); Thread.sleep(10)
        }
        rule.mainClock.autoAdvance = true
        waitIdle(vm)
        assertEquals(GameViewModel.MdStage.REPORT, vm.mdStage)
        shot("24_report")
        assertTrue("match was recorded", vm.career!!.userFixtures().any { it.played })
        rule.onNodeWithText("Team Talk Akhir Laga").performClick(); rule.waitForIdle()
        rule.onNodeWithText(TeamTalk.Tone.PRAISE.id).performClick(); rule.waitForIdle()
        rule.onNodeWithText("Lanjut").performClick(); rule.waitForIdle()
        assertEquals(GameViewModel.MdStage.PRESS_POST, vm.mdStage)
        rule.onNodeWithText("Kirim asisten (lewati)").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Lanjut").performClick()
        waitIdle(vm)
        assertEquals(Screen.Home, vm.screen)
        assertNull(vm.matchDay)
        shot("25_home_after")
        go(vm, Screen.DataHub, "26_datahub")
        go(vm, Screen.International, "27_international")
        // Save and reload
        rule.runOnUiThread { vm.save() }; waitIdle(vm)
        val slot = vm.slot
        assertTrue(vm.slots().any { it.slot == slot })
        rule.runOnUiThread { vm.load(slot) }; waitIdle(vm)
        assertEquals(Screen.Home, vm.screen)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletLayout() {
        val vm = vm()
        val club = vm.previewWorld(7).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        rule.runOnUiThread { vm.newCareer("Tab", "Let", 1, club, 7, ManagerStyle("4-2-3-1", 0.2, 0.5, 0.5, 0.5, 0.5)) }
        pumpUntil { (vm.career != null && !vm.busy) || vm.toast != null }
        assertNull(vm.toast)
        shot("tab_01_home")
        go(vm, Screen.Squad, "tab_02_squad")
        go(vm, Screen.Tactics, "tab_03_tactics")
        go(vm, Screen.PlayerDetail(vm.world!!.squad(club).maxBy { it.ca }.id), "tab_04_player")
        go(vm, Screen.Dynamics, "tab_05_dynamics")
    }
}
