package fm.engine

import fm.core.*
import fm.engine.game.Career
import fm.engine.game.Validator
import fm.engine.match.*
import fm.engine.save.SaveStore
import java.io.File
import java.nio.file.Files
import kotlin.test.*

object WorldHash {
    fun of(w: World): Long {
        var h = 1125899906842597L
        fun mix(v: Long) { h = 31 * h + v }
        mix(w.today.toLong()); mix(w.season.toLong()); mix(w.nextId)
        for (p in w.players.values) { mix(p.id); mix(p.clubId); mix(p.ca.toLong()); for (a in p.attrs) mix(a.toLong()); mix(p.season.goals.toLong()) }
        for (c in w.clubs.values) { mix(c.id); mix(c.balance); mix(c.leagueId.toLong()); mix(c.reputation.toLong()) }
        for (cs in w.compSeasons.values) for (f in cs.fixtures) { mix(f.homeGoals.toLong()); mix(f.awayGoals.toLong()) }
        return h
    }
}

class DeterminismAndSaveTest {
    private fun run(days: Int, seed: Long): Career {
        val club = TestWorlds.world(seed).clubs.values.first { it.leagueId == fm.engine.world.WorldGenerator.IDN_2 }.id
        val c = Career.newCareer(seed, TestWorlds.manager(), club)
        repeat(days) { if (c.advanceDay().userMatchPending) { c.playUserMatchInstant(); c.advanceDay() } }
        return c
    }

    @Test
    fun sameSeedSameWorld() {
        val a = run(120, 99)
        val b = run(120, 99)
        assertEquals(WorldHash.of(a.world), WorldHash.of(b.world))
        val c = run(120, 100)
        assertNotEquals(WorldHash.of(a.world), WorldHash.of(c.world))
    }

    @Test
    fun matchEngineIsDeterministic() {
        val w = TestWorlds.world(5)
        val clubs = w.clubs.values.take(2)
        fun play() = MatchEngine2D(MatchSetup(Lineups.pick(w, clubs[0]), Lineups.pick(w, clubs[1]), 777)).runToEnd()
        val r1 = play(); val r2 = play()
        assertEquals(r1.homeGoals, r2.homeGoals); assertEquals(r1.awayGoals, r2.awayGoals)
        assertEquals(r1.events.size, r2.events.size)
        assertEquals(r1.stats[0].passes, r2.stats[0].passes)
    }

    @Test
    fun saveLoadRoundTripPreservesWorldAndContinuesIdentically() {
        val dir = Files.createTempDirectory("fmsave").toFile()
        try {
            val c = run(60, 3)
            val store = SaveStore(dir)
            val info = store.save(c.world, "slot1")
            assertTrue(info.sizeBytes > 0)
            val loaded = store.load("slot1")
            assertEquals(WorldHash.of(c.world), WorldHash.of(loaded))
            assertTrue(Validator.validate(loaded).isEmpty())
            // Continue both copies: they must stay identical (no hidden state lost).
            val c2 = Career(loaded)
            repeat(40) {
                if (c.advanceDay().userMatchPending) { c.playUserMatchInstant(); c.advanceDay() }
                if (c2.advanceDay().userMatchPending) { c2.playUserMatchInstant(); c2.advanceDay() }
            }
            assertEquals(WorldHash.of(c.world), WorldHash.of(c2.world))
            assertEquals(1, store.list().size)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun corruptSaveIsDetectedAndBackupRecovers() {
        val dir = Files.createTempDirectory("fmsave2").toFile()
        try {
            val c = run(10, 4)
            val store = SaveStore(dir)
            store.save(c.world, "s")
            c.advanceDay()
            store.save(c.world, "s") // previous file becomes .bak1
            val f = File(dir, "s.fmsave")
            val bytes = f.readBytes(); bytes[bytes.size - 10] = (bytes[bytes.size - 10] + 1).toByte(); f.writeBytes(bytes)
            assertFailsWith<SaveStore.CorruptSaveException> { store.load("s") }
            val (w, from) = store.loadWithRecovery("s")
            assertEquals("s.fmsave.bak1", from)
            assertTrue(Validator.validate(w).isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
