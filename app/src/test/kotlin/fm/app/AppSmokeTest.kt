package fm.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fm.core.ManagerStyle
import fm.engine.world.WorldGenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the real Activity and Compose UI on the JVM (Robolectric): start screen, new career,
 * every main screen, a live match, and save/load. Catches crashes in UI code without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun vm(): GameViewModel {
        var v: GameViewModel? = null
        rule.activityRule.scenario.onActivity { v = androidx.lifecycle.ViewModelProvider(it)[GameViewModel::class.java] }
        return v!!
    }

    /** Pumps the Robolectric main looper while background work runs (coroutines resume on Main). */
    private fun pumpUntil(timeoutMs: Long = 120_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timeout")
            rule.runOnUiThread { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16)) }
            Thread.sleep(20)
        }
        repeat(5) { rule.runOnUiThread { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16)) } }
        rule.waitForIdle()
    }

    private fun waitIdle(vm: GameViewModel) = pumpUntil { !vm.busy }

    @Test
    fun fullUiFlow() {
        rule.onNodeWithText("Karier Baru").assertExists()
        val vm = vm()
        rule.onNodeWithText("Karier Baru").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Profil Manajer").assertExists()
        // Start a career at an Indonesian top-flight club
        val club = vm.previewWorld(123).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        rule.runOnUiThread { vm.newCareer("Uji", "Coba", 1, club, 123, ManagerStyle("4-3-3", 0.2, 0.5, 0.5, 0.5, 0.5)) }
        pumpUntil { (vm.career != null && !vm.busy) || vm.toast != null }
        assertNull("no error: " + vm.toast, vm.toast)
        assertEquals(Screen.Home, vm.screen)
        // Visit every screen
        val screens = listOf(Screen.Squad, Screen.Tactics, Screen.Transfers, Screen.Competitions, Screen.Training, Screen.Scouting,
            Screen.Club, Screen.Finances, Screen.Inbox, Screen.Profile, Screen.Settings, Screen.Home)
        for (s in screens) { rule.runOnUiThread { vm.navigate(s) }; rule.waitForIdle() }
        val pid = vm.world!!.squad(club).first().id
        rule.runOnUiThread { vm.navigate(Screen.PlayerDetail(pid)) }; rule.waitForIdle()
        rule.runOnUiThread { vm.navigate(Screen.ClubDetail(vm.world!!.clubs.keys.last())) }; rule.waitForIdle()
        rule.runOnUiThread { vm.navigate(Screen.Home, true) }; rule.waitForIdle()
        // Advance to the first match day
        var guard = 0
        while (vm.career!!.userFixtureToday() == null && guard++ < 20) { rule.runOnUiThread { vm.continueGame() }; waitIdle(vm) }
        assertNotNull("reached a match day", vm.career!!.userFixtureToday())
        val texts = rule.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString() }
        println("SCREEN=" + vm.screen + " today=" + fm.core.Cal.format(vm.world!!.today) + " fixture=" + vm.career!!.userFixtureToday()?.id + " v=" + vm.version + " busy=" + vm.busy + " TEXTS=" + texts.take(40))
        rule.onNodeWithText("Tonton Laga").assertExists()
        // The live match animates every frame, so drive the frame clock manually.
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { vm.startMatch() }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(Screen.Match, vm.screen)
        val engine = vm.engine!!
        repeat(300) { // ~5 s of frames
            rule.runOnUiThread { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16)) }
            rule.mainClock.advanceTimeByFrame()
        }
        assertTrue("engine advanced during frames", engine.tick > 0)
        rule.onNodeWithText("Statistik").performClick(); rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Ganti").performClick(); rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Taktik").performClick(); rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Komentar").performClick(); rule.mainClock.advanceTimeBy(100)
        rule.runOnUiThread { vm.finishMatch() }
        val end = System.currentTimeMillis() + 120_000
        while ((vm.busy || vm.screen == Screen.Match) && System.currentTimeMillis() < end) {
            rule.runOnUiThread { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16)) }
            rule.mainClock.advanceTimeByFrame(); Thread.sleep(10)
        }
        rule.mainClock.autoAdvance = true
        waitIdle(vm)
        assertEquals(Screen.Home, vm.screen)
        assertTrue("match was recorded", vm.career!!.userFixtures().any { it.played })
        // Save and reload
        rule.runOnUiThread { vm.save() }; waitIdle(vm)
        val slot = vm.slot
        assertTrue(vm.slots().any { it.slot == slot })
        rule.runOnUiThread { vm.load(slot) }; waitIdle(vm)
        assertEquals(Screen.Home, vm.screen)
    }
}
