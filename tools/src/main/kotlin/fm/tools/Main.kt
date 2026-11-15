package fm.tools

import fm.core.*
import fm.engine.ability.Ability
import fm.engine.competition.CompetitionEngine
import fm.engine.game.Career
import fm.engine.game.Validator
import fm.engine.match.EvType
import fm.engine.save.SaveStore
import fm.engine.world.WorldGenerator
import java.io.File

/**
 * Desktop/terminal tools for the engine.
 *
 *   simulate [--seasons N] [--seed S]   headless long-term simulation with a per-season report
 *   play [--seed S] [--slot NAME]       text-mode career (Indonesian), saves to ./saves
 *   validate --slot NAME                check a save file
 *   export-pack [--seed S] [--out F]    export the default world as an editable JSON database pack
 *   validate-pack --file F              validate a JSON pack or a players CSV
 */
fun main(args: Array<String>) {
    val cmd = args.firstOrNull() ?: "play"
    fun opt(name: String, def: String) = args.toList().zipWithNext().firstOrNull { it.first == "--$name" }?.second ?: def
    when (cmd) {
        "simulate" -> simulate(opt("seasons", "5").toInt(), opt("seed", "2026").toLong())
        "validate" -> {
            val w = SaveStore(File("saves")).load(opt("slot", "karier1"))
            val issues = Validator.validate(w)
            println(if (issues.isEmpty()) "OK: save valid (${w.players.size} pemain, musim ${w.season})" else issues.joinToString("\n"))
        }
        "export-pack" -> {
            val w = WorldGenerator.generate(opt("seed", "2026").toLong(), defaultManager())
            val out = File(opt("out", "database.json"))
            out.writeText(fm.engine.data.Packs.toJson(fm.engine.data.Packs.export(w, "Default world")))
            println("Exported ${w.clubs.size} clubs and ${w.players.size} players to ${out.path}")
        }
        "validate-pack" -> {
            val f = File(opt("file", "database.json"))
            val text = f.readText()
            val pack = if (text.trimStart().startsWith("{")) fm.engine.data.Packs.parseJson(text) else fm.engine.data.Packs.parsePlayersCsv(text, f.name)
            val issues = fm.engine.data.Packs.validate(pack, WorldGenerator.generate(1, defaultManager()))
            issues.forEach { println((if (it.error) "ERROR " else "WARN  ") + it.message) }
            println(if (issues.none { it.error }) "OK: ${pack.clubs.size} clubs, ${pack.players.size} players" else "INVALID: ${issues.count { it.error }} errors")
        }
        else -> TextGame(opt("seed", System.currentTimeMillis().toString()).toLong(), opt("slot", "karier1")).run()
    }
}

private fun defaultManager() = ManagerProfile("Manajer", "Baru", 1, Cal.of(1988, 5, 17), "Lisensi B AFC", "Pemain amatir",
    ManagerStyle("4-3-3", 0.2, 0.5, 0.6, 0.6, 0.5), reputation = 35)

fun simulate(seasons: Int, seed: Long) {
    val world0 = WorldGenerator.generate(seed, defaultManager())
    val club = world0.clubs.values.first { it.leagueId == WorldGenerator.IDN_1 }.id
    val career = Career.newCareer(seed, defaultManager(), club)
    val w = career.world
    println("Simulasi $seasons musim, seed $seed, ${w.players.size} pemain, ${w.clubs.size} klub")
    val start = w.season
    while (w.season < start + seasons) {
        val t0 = System.currentTimeMillis()
        val s = w.season
        while (w.season == s) {
            if (career.advanceDay().userMatchPending) career.playUserMatchInstant()
            if (w.manager.clubId < 0 && w.manager.jobOffers.isNotEmpty()) career.acceptJob(w.manager.jobOffers.first())
        }
        val sum = w.seasonHistory.last()
        val issues = Validator.validate(w)
        println("Musim $s: juara IDN1=${sum.champions[WorldGenerator.IDN_1]}, piala=${sum.champions[WorldGenerator.IDN_CUP]}, " +
            "top skor=${sum.topScorer}, pemain=${w.players.size}, ${System.currentTimeMillis() - t0} ms, validasi=${if (issues.isEmpty()) "OK" else issues.size}")
    }
}

/** Minimal interactive career in the terminal, using the same Career API as the Android app. */
class TextGame(private val seed: Long, private val slot: String) {
    private val store = SaveStore(File("saves"))
    private lateinit var career: Career
    private val w get() = career.world
    private val lang = Lang.ID

    fun run() {
        println("=== MANAJER BOLA (mode teks) ===")
        career = if (store.info(slot) != null && ask("Lanjutkan simpanan '$slot'? (y/n)") == "y") Career(store.load(slot)) else newCareer()
        loop()
    }

    private fun ask(q: String): String { print("$q > "); return readlnOrNull()?.trim() ?: "q" }

    private fun newCareer(): Career {
        val name = ask("Nama Anda").ifBlank { "Manajer Baru" }
        val parts = name.split(" ", limit = 2)
        val profile = defaultManager().also { it.firstName = parts[0]; it.lastName = parts.getOrElse(1) { "" } }
        val tmp = WorldGenerator.generate(seed, profile)
        val choices = Career.clubChoices(tmp).filter { it.nationId == 1 }
        choices.forEachIndexed { i, c -> if (i < 56) println("%2d. %-26s %-6s rep %d".format(i + 1, c.name, tmp.comp(c.leagueId).shortName, c.reputation)) }
        val pick = ask("Pilih klub (nomor) atau 0 untuk mulai tanpa klub").toIntOrNull() ?: 1
        return Career.newCareer(seed, profile, if (pick in 1..choices.size) choices[pick - 1].id else null)
    }

    private fun loop() {
        while (true) {
            val club = w.userClub
            println("\n${Cal.format(w.today, lang)} | ${club?.name ?: "Tanpa klub"} | Saldo ${club?.let { Money.format(it.balance) } ?: "-"} | Dewan ${club?.boardConfidence ?: 0}%")
            career.nextUserFixture()?.let { f -> println("Laga berikut: ${Cal.formatShort(f.day)} ${w.club(f.homeId).name} vs ${w.club(f.awayId).name} (${w.comp(f.compId).shortName})") }
            when (ask("[l]anjut  [s]kuad  [k]lasemen  [t]aktik  [i]nbox  [p]asar  [o]fer kerja  [v] simpan  [q]uit")) {
                "l" -> advance()
                "s" -> squad()
                "k" -> table()
                "t" -> tactics()
                "i" -> inbox()
                "p" -> market()
                "o" -> jobs()
                "v" -> save()
                "q" -> { save(); return }
            }
        }
    }

    private fun advance() {
        val r = career.continueGame()
        if (r.userMatchPending || career.userFixtureToday() != null) {
            val f = career.userFixtureToday() ?: return
            println("HARI PERTANDINGAN: ${w.club(f.homeId).name} vs ${w.club(f.awayId).name}")
            val res = career.playUserMatchInstant() ?: return
            for (e in res.events.filter { it.type in setOf(EvType.GOAL, EvType.PEN_GOAL, EvType.RED) }) {
                val p = w.players[e.playerId]?.name ?: "?"
                println("  ${e.minute}' ${if (e.type == EvType.RED) "KARTU MERAH" else "GOL"} $p (${if (e.team == 0) w.club(f.homeId).shortName else w.club(f.awayId).shortName})")
            }
            println("  Skor akhir: ${res.homeGoals}-${res.awayGoals}" + (if (res.penHome >= 0) " (pen ${res.penHome}-${res.penAway})" else "") +
                "  | Tembakan ${res.stats[0].shots}-${res.stats[1].shots}  xG ${"%.2f".format(res.stats[0].xg)}-${"%.2f".format(res.stats[1].xg)}")
            career.advanceDay()
            if (w.settings.autosave) store.save(w, slot)
        }
    }

    private fun squad() {
        val club = w.userClub ?: return
        println("%-24s %-4s %3s %5s %6s %5s".format("Nama", "Pos", "Usia", "CA", "Kond", "Moral"))
        for (p in w.squad(club.id).sortedWith(compareBy({ it.position.ordinal }, { -it.ca }))) {
            println("%-24s %-4s %3d %5d %5d%% %5d %s".format(p.name.take(24), p.position.label(lang), p.age(w.today), p.ca, p.condition, p.morale,
                if (p.injuryDays > 0) "CEDERA ${p.injuryDays}h" else if (p.suspended > 0) "SKORSING" else ""))
        }
    }

    private fun table() {
        val club = w.userClub
        val comp = club?.leagueId ?: WorldGenerator.IDN_1
        println(w.comp(comp).name(lang))
        career.leagueTable(comp).forEachIndexed { i, r ->
            println("%2d. %-24s %2d %2d %2d %2d %3d:%-3d %3d".format(i + 1, w.club(r.clubId).name.take(24), r.played, r.won, r.drawn, r.lost, r.gf, r.ga, r.points))
        }
    }

    private fun tactics() {
        val club = w.userClub ?: return
        Formation.ALL.forEachIndexed { i, f -> println("${i + 1}. ${f.name}") }
        val f = ask("Formasi (nomor)").toIntOrNull()?.let { Formation.ALL.getOrNull(it - 1) } ?: return
        Mentality.entries.forEachIndexed { i, m -> println("${i + 1}. ${m.label(lang)}") }
        val m = ask("Mentalitas").toIntOrNull()?.let { Mentality.entries.getOrNull(it - 1) } ?: Mentality.BALANCED
        career.setTactic(club.tactic.copy(formation = f, instructions = club.tactic.instructions.copy(mentality = m), lineup = emptyList()))
        println("Taktik disimpan: ${f.name}, ${m.label(lang)}")
    }

    private fun inbox() {
        w.inbox.takeLast(12).forEach { println("${Cal.formatShort(it.day)}  ${career.message(it)}"); it.read = true }
    }

    private fun market() {
        val club = w.userClub ?: return
        println("Anggaran transfer ${Money.format(club.transferBudget)}. Jendela ${if (fm.engine.market.Transfers.windowOpen(w)) "TERBUKA" else "tertutup"}")
        val pos = ask("Posisi dicari (mis. ST, DC, GK) atau kosong").uppercase()
        val p = Position.entries.firstOrNull { it.code == pos }
        val list = fm.engine.market.Transfers.searchPlayers(w, p?.let { setOf(it) }, maxValue = club.transferBudget).take(15)
        list.forEachIndexed { i, pl ->
            val ref = w.clubs[pl.clubId]?.name ?: "Bebas transfer"
            println("%2d. %-22s %-4s %2d th  CA~%3d  nilai %-12s %s".format(i + 1, pl.name.take(22), pl.position.code, pl.age(w.today),
                Ability.caAt(pl.attrs, pl.position) / 5 * 5, Money.format(pl.value), ref))
        }
        val k = ask("Tawar pemain nomor (kosong batal)").toIntOrNull() ?: return
        val target = list.getOrNull(k - 1) ?: return
        val demand = fm.engine.market.Transfers.wageDemand(w, target, club)
        if (target.clubId < 0) {
            println(if (career.signFreeAgent(target.id, demand, 2)) "Ditandatangani!" else "Pemain menolak.")
            return
        }
        val fee = ask("Biaya (juta Rp), nilai ${Money.format(target.value)}").toLongOrNull()?.times(Money.JUTA) ?: target.value
        println("Hasil: ${career.offer(target.id, fee, demand, 3)}")
    }

    private fun jobs() {
        if (w.manager.jobOffers.isEmpty()) { println("Tidak ada tawaran."); return }
        w.manager.jobOffers.forEachIndexed { i, id -> println("${i + 1}. ${w.club(id).name}") }
        val k = ask("Terima tawaran nomor").toIntOrNull() ?: return
        w.manager.jobOffers.getOrNull(k - 1)?.let { println(if (career.acceptJob(it)) "Selamat!" else "Gagal") }
    }

    private fun save() { val i = store.save(w, slot); println("Tersimpan ke saves/${i.slot}.fmsave (${i.sizeBytes / 1024} KB)") }
}
