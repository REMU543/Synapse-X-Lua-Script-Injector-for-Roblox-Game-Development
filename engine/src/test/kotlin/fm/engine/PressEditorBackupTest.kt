package fm.engine

import fm.core.*
import fm.engine.dynamics.Press
import fm.engine.game.Career
import fm.engine.save.SaveStore
import fm.engine.world.WorldGenerator
import java.nio.file.Files
import kotlin.test.*

class PressEditorBackupTest {
    private fun career(seed: Long): Career {
        val club = TestWorlds.world(seed).clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
        return Career.newCareer(seed, TestWorlds.manager(), club)
    }

    @Test
    fun pressQuestionsFollowContext() {
        val c = career(81); val w = c.world
        var guard = 0
        while (c.userFixtureToday() == null && guard++ < 60) c.advanceDay()
        val f = c.userFixtureToday()!!
        val club = w.userClub!!
        // Create an injury crisis and board pressure
        w.squad(club.id).filter { !it.reserve }.take(4).forEach { it.injuryDays = 20 }
        club.boardConfidence = 20
        val pc = Press.preMatch(w, f)
        assertEquals("press_pre_opponent", pc.questions.first().key)
        assertTrue(pc.questions.size in 2..3)
        assertTrue(pc.questions.any { it.key == "press_pre_injuries" }, pc.questions.map { it.key }.toString())
        assertTrue(pc.questions.all { I18n.hasKey(it.key) })
        // Specific consequence: confident answer under pressure lifts board confidence
        val q = PressQuestion(10, "press_pre_pressure")
        val before = club.boardConfidence
        Press.answer(w, q, Press.Answer.CONFIDENT)
        assertTrue(club.boardConfidence > before)
    }

    @Test
    fun editorChangesClubAndPlayer() {
        val c = career(82); val w = c.world
        val club = w.userClub!!
        val other = w.clubs.values.first { it.id != club.id }
        assertTrue(c.editClub(club.id, Career.ClubEdit(other.name, "X", "C", "S", 50, 0xFF000000, 0xFFFFFFFF, 10000)).isNotEmpty(), "duplicate names rejected")
        assertTrue(c.editClub(club.id, Career.ClubEdit("Klub Baru", "kbr", "Kota", "Stadion", 77, 0xFF123456, 0xFFFFFFFF, 25000)).isEmpty())
        assertEquals("Klub Baru", club.name); assertEquals("KBR", club.shortName); assertEquals(77, club.reputation); assertEquals(25000, club.stadiumCapacity)
        val p = w.squad(club.id).first()
        assertTrue(c.editPlayerName(p.id, "Nama", "Baru"))
        assertEquals("Nama Baru", p.name)
        assertFalse(c.editPlayerName(p.id, "x", " "))
    }

    @Test
    fun backupImportNeverOverwritesNewerLocalSave() {
        val dir = Files.createTempDirectory("backup").toFile()
        try {
            val c = career(83); val store = SaveStore(dir)
            store.save(c.world, "s1", nowMillis = 1000)
            val backup = store.exportBytes("s1")
            repeat(5) { c.advanceDay() }
            store.save(c.world, "s1", nowMillis = 2000) // local is now newer
            val check = store.inspect(backup)
            assertTrue(check.localIsNewer)
            assertFailsWith<IllegalStateException> { store.importBytes(backup, "s1") }
            // Importing as a copy works; explicit overwrite works too and keeps the newer one as .bak1
            store.importBytes(backup, "s1_copy")
            assertEquals("s1_copy", store.info("s1_copy")!!.slot)
            assertTrue(store.list().any { it.slot == "s1_copy" })
            val restored = store.importBytes(backup, "s1", overwriteNewer = true)
            assertEquals(check.info.day, restored.day)
            assertTrue(java.io.File(dir, "s1.fmsave.bak1").exists())
            // Corrupt backups are rejected
            val bad = backup.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
            assertFailsWith<SaveStore.CorruptSaveException> { store.inspect(bad) }
        } finally { dir.deleteRecursively() }
    }
}
