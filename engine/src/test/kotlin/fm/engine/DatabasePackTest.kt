package fm.engine

import fm.core.*
import fm.engine.data.*
import fm.engine.game.Career
import fm.engine.game.Validator
import fm.engine.world.WorldGenerator
import kotlin.test.*

class DatabasePackTest {
    private val pack = DatabasePack(
        name = "Test Pack", author = "tester",
        clubs = listOf(
            PackClub("IDN1", "Persatuan Uji Jakarta", "PUJ", "Jakarta", 80, "Stadion Uji", 50000, "#C62828", "#FFFFFF"),
            PackClub("IDN1", "Klub Contoh Bandung", "KCB", "Bandung", 75),
        ),
        players = listOf(
            PackPlayer("Persatuan Uji Jakarta", "Andi", "Contoh", "IDN", "1998-04-12", "ST", "RIGHT", 178, 120, 140, mapOf("FINISHING" to 17, "PACE" to 16)),
            PackPlayer("Persatuan Uji Jakarta", "Budi", "Sampel", "IDN", "2003-09-01", "DC", ability = 95),
            PackPlayer("", "Bebas", "Agen", "BRA", "1995-01-01", "MC", ability = 100),
        ),
    )

    @Test
    fun validationFindsErrors() {
        val w = TestWorlds.world(71)
        assertTrue(Packs.validate(pack, w).none { it.error })
        val bad = pack.copy(
            clubs = pack.clubs + PackClub("XXX", "Nowhere FC") + PackClub("IDN1", "Persatuan Uji Jakarta"),
            players = pack.players + PackPlayer("Unknown Club", "", "Bad", "ZZZ", "19-01-01", "QQ", attributes = mapOf("FINISHING" to 25)),
        )
        val errors = Packs.validate(bad, w).filter { it.error }.map { it.message }
        assertTrue(errors.any { "unknown league" in it })
        assertTrue(errors.any { "duplicate club name" in it })
        assertTrue(errors.any { "unknown nation" in it })
        assertTrue(errors.any { "unknown position" in it })
        assertTrue(errors.any { "birthDate" in it })
        assertTrue(errors.any { "unknown club" in it })
        assertTrue(errors.any { "must be 1–20" in it })
        val tooMany = DatabasePack(name = "x", clubs = (1..19).map { PackClub("IDN1", "Club $it") })
        assertTrue(Packs.validate(tooMany, w).any { it.error && "places" in it.message })
        assertFailsWith<IllegalArgumentException> { Packs.apply(bad, TestWorlds.world(72)) }
    }

    @Test
    fun applyRenamesClubsAndCreatesPlayers() {
        val w = TestWorlds.world(73)
        val report = Packs.apply(pack, w)
        assertEquals(2, report.clubsRenamed)
        assertEquals(3, report.playersCreated)
        val club = w.clubs.values.first { it.name == "Persatuan Uji Jakarta" }
        assertEquals(0xFFC62828, club.primaryColor)
        assertEquals("Stadion Uji", club.stadiumName)
        val st = w.squad(club.id).first { it.lastName == "Contoh" }
        assertEquals(17, st.attrInt(Attr.FINISHING))
        assertEquals(140, st.pa)
        assertTrue(w.squad(club.id).size >= 20, "topped up to a playable squad")
        assertTrue(w.players.values.any { it.lastName == "Agen" && it.clubId < 0 })
        assertTrue(Validator.validate(w).isEmpty(), Validator.validate(w).take(5).joinToString())
    }

    @Test
    fun exportImportRoundTripAndCsv() {
        val w = TestWorlds.world(74)
        val exported = Packs.export(w, "Export")
        val text = Packs.toJson(exported)
        val back = Packs.parseJson(text)
        assertEquals(exported.clubs.size, back.clubs.size)
        assertEquals(exported.players.size, back.players.size)
        val w2 = TestWorlds.world(75)
        assertTrue(Packs.validate(back, w2).none { it.error }, Packs.validate(back, w2).filter { it.error }.take(3).joinToString())
        Packs.apply(back, w2)
        assertEquals(w.clubs.values.map { it.name }.sorted(), w2.clubs.values.map { it.name }.sorted())
        val csv = "club,firstName,lastName,nation,birthDate,position,ability,FINISHING\n\"Klub, Koma\",Satu,Dua,IDN,2000-02-02,ST,110,15\n,Tiga,Empat,JPN,1999-03-03,GK,90,\n"
        val p = Packs.parsePlayersCsv(csv)
        assertEquals(2, p.players.size)
        assertEquals("Klub, Koma", p.players[0].club)
        assertEquals(15, p.players[0].attributes["FINISHING"])
    }

    @Test
    fun careerWithPackPlays() {
        val c = Career.newCareer(76, TestWorlds.manager(), null, pack)
        val w = c.world
        val club = w.clubs.values.first { it.name == "Klub Contoh Bandung" }
        if (club.id in w.manager.jobOffers) assertTrue(c.acceptJob(club.id))
        var guard = 0
        while (w.today < Cal.of(2026, 9, 30) && guard++ < 300) { if (c.advanceDay().userMatchPending) c.playUserMatchInstant() }
        assertTrue(Validator.validate(w).isEmpty())
        assertTrue(w.compSeasons.getValue(WorldGenerator.IDN_1).fixtures.any { it.played })
    }
}
