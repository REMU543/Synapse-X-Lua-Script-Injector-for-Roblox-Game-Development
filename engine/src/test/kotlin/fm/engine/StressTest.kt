package fm.engine

import fm.core.*
import fm.engine.game.Career
import fm.engine.game.Validator
import fm.engine.save.SaveStore
import fm.engine.world.WorldGenerator
import java.nio.file.Files
import kotlin.test.*

/**
 * Long-term world simulation. Run with: ./gradlew :engine:test --tests '*StressTest*' -Pstress
 * Seasons can be set with -Dseasons=50 (default 30).
 */
class StressTest {
    @Test
    fun thirtySeasonCareer() {
        val seasons = (System.getProperty("seasons") ?: "30").toInt()
        val club = TestWorlds.world(2026).clubs.values.first { it.leagueId == WorldGenerator.IDN_2 }.id
        val career = Career.newCareer(2026, TestWorlds.manager(), club)
        val w = career.world
        val startSeason = w.season
        val initialPlayers = w.players.size
        val dir = Files.createTempDirectory("stress").toFile()
        val store = SaveStore(dir)
        val report = StringBuilder()
        report.appendLine("season | players | avgCA top-league | retired(archive) | transfers | IDN1 champion | user club pos | ms | save KB")
        var promotedOrRelegated = false
        val idn1Start = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_1 }.map { it.id }.toSet()
        while (w.season < startSeason + seasons) {
            val t0 = System.nanoTime()
            val s = w.season
            var guard = 0
            while (w.season == s && guard++ < 1000) {
                val r = career.advanceDay()
                if (r.userMatchPending) career.playUserMatchInstant()
                if (w.manager.clubId < 0 && w.manager.jobOffers.isNotEmpty()) career.acceptJob(w.manager.jobOffers.first())
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            val issues = Validator.validate(w)
            assertTrue(issues.isEmpty(), "season $s: " + issues.take(10).joinToString("; "))
            val info = store.save(w, "stress")
            val top = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_1 }.flatMap { w.squad(it.id) }.map { it.ca }.average()
            val idn1 = w.clubs.values.filter { it.leagueId == WorldGenerator.IDN_1 }.map { it.id }.toSet()
            if (idn1 != idn1Start) promotedOrRelegated = true
            val pos = w.userClub?.let { fm.engine.competition.CompetitionEngine.position(w, it.id) } ?: 0
            report.appendLine("$s | ${w.players.size} | ${"%.1f".format(top)} | ${w.archive.size} | ${w.transfers.size} | " +
                "${w.seasonHistory.last().champions[WorldGenerator.IDN_1]} | ${w.userClub?.name ?: "-"} $pos | $ms | ${info.sizeBytes / 1024}")
        }
        println(report)
        Files.writeString(dir.toPath().resolve("../stress-report.txt").normalize(), report.toString())
        // Population stays bounded and the world keeps producing players.
        assertTrue(w.players.size in (initialPlayers * 0.7).toInt()..(initialPlayers * 1.5).toInt(), "population ${w.players.size} vs $initialPlayers")
        val originalIds = (1000L..3600L)
        assertTrue(w.players.keys.count { it in originalIds } < initialPlayers / 10, "most of the original generation retired")
        assertTrue(promotedOrRelegated)
        assertTrue(w.archive.isNotEmpty())
        // Reload the final save and verify it.
        val loaded = store.load("stress")
        assertEquals(WorldHash.of(w), WorldHash.of(loaded))
        dir.deleteRecursively()
    }
}
