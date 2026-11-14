package fm.core

/**
 * Localised message templates. Engine code stores only keys + arguments, so saves are
 * language-neutral and the UI can switch language at any time. Indonesian is the default.
 */
object I18n {
    private val id = mapOf(
        "welcome" to "Selamat datang di {0}! Dewan berharap tim finis di posisi {1}. Semoga sukses, {2}.",
        "welcome_unemployed" to "Karier Anda dimulai tanpa klub. Lamar pekerjaan di menu Profil untuk mendapat tawaran.",
        "result_win" to "Kemenangan {0}-{1} atas {2}.",
        "result_draw" to "Hasil imbang {0}-{1} melawan {2}.",
        "result_loss" to "Kekalahan {0}-{1} dari {2}.",
        "injury" to "{0} cedera ({1}) dan absen sekitar {2} hari.",
        "contract_expiring" to "Kontrak {0} berakhir akhir musim ini. Perpanjang di profil pemain jika ingin mempertahankannya.",
        "player_left_free" to "{0} meninggalkan klub setelah kontraknya habis.",
        "transfer_done_in" to "{0} resmi bergabung dari {1} dengan biaya {2}.",
        "transfer_done_out" to "{0} resmi dijual ke {1} dengan biaya {2}.",
        "offer_received" to "{0} menawar {1} sebesar {2}. Tawaran diterima otomatis bila Anda menandai pemain dijual.",
        "youth_intake" to "{0} pemain muda baru masuk akademi. Yang paling menjanjikan: {1} ({2}).",
        "board_warning" to "Dewan tidak puas dengan hasil terakhir. Kepercayaan dewan: {0}%.",
        "board_sacked" to "Dewan memutuskan memberhentikan Anda dari {0}.",
        "board_praise" to "Dewan sangat puas dengan performa tim musim ini.",
        "job_offer" to "{0} tertarik merekrut Anda sebagai manajer.",
        "job_hired" to "Anda resmi menjadi manajer {0}.",
        "season_end" to "Musim {0} berakhir. {1} finis di posisi {2}.",
        "promoted" to "{0} promosi ke {1}!",
        "relegated" to "{0} terdegradasi ke {1}.",
        "champion" to "{0} menjadi juara {1}!",
        "retired" to "{0} ({1} th) mengumumkan pensiun.",
        "finance_month" to "Laporan keuangan bulanan: saldo {0}.",
        "scout_report" to "Laporan pemandu bakat: {0} pemain di {1} kini lebih dikenal.",
        "manager_sacked" to "{0} dipecat oleh {1}.",
        "manager_hired" to "{0} ditunjuk sebagai manajer baru {1}.",
        "renewed" to "{0} menandatangani kontrak baru hingga {1}.",
        "ev_goal" to "GOL! {0} mencetak gol untuk {1}!",
        "ev_penalty_goal" to "GOL! {0} sukses mengeksekusi penalti!",
        "ev_penalty_miss" to "Penalti {0} gagal!",
        "ev_save" to "Penyelamatan gemilang oleh {0}!",
        "ev_miss" to "{0} melepaskan tembakan, tapi melebar.",
        "ev_blocked" to "Tembakan {0} diblok.",
        "ev_yellow" to "Kartu kuning untuk {0}.",
        "ev_red" to "Kartu merah! {0} diusir keluar lapangan.",
        "ev_foul" to "Pelanggaran oleh {0}.",
        "ev_offside" to "{0} terjebak offside.",
        "ev_corner" to "Tendangan sudut untuk {0}.",
        "ev_sub" to "Pergantian {0}: {1} masuk menggantikan {2}.",
        "ev_injury" to "{0} cedera dan harus ditarik keluar.",
        "ev_kickoff" to "Pertandingan dimulai!",
        "ev_halftime" to "Turun minum. Skor {0}.",
        "ev_fulltime" to "Peluit panjang! Skor akhir {0}.",
        "ev_secondhalf" to "Babak kedua dimulai.",
        "ev_chance" to "Peluang emas untuk {0}!",
        "ev_tactic" to "{0} mengubah taktik.",
    )
    private val en = mapOf(
        "welcome" to "Welcome to {0}! The board expects a finish around position {1}. Good luck, {2}.",
        "welcome_unemployed" to "Your career starts without a club. Apply for jobs from the Profile screen.",
        "result_win" to "A {0}-{1} win over {2}.",
        "result_draw" to "A {0}-{1} draw with {2}.",
        "result_loss" to "A {0}-{1} defeat to {2}.",
        "injury" to "{0} is injured ({1}) and out for about {2} days.",
        "contract_expiring" to "{0}'s contract expires at the end of the season. Renew it from the player profile.",
        "player_left_free" to "{0} has left the club after his contract expired.",
        "transfer_done_in" to "{0} has joined from {1} for {2}.",
        "transfer_done_out" to "{0} has been sold to {1} for {2}.",
        "offer_received" to "{0} bid {2} for {1}. Bids are accepted automatically for transfer-listed players.",
        "youth_intake" to "{0} new youngsters joined the academy. Most promising: {1} ({2}).",
        "board_warning" to "The board is unhappy with recent results. Confidence: {0}%.",
        "board_sacked" to "The board has dismissed you from {0}.",
        "board_praise" to "The board is delighted with this season.",
        "job_offer" to "{0} are interested in appointing you as manager.",
        "job_hired" to "You are the new manager of {0}.",
        "season_end" to "Season {0} is over. {1} finished in position {2}.",
        "promoted" to "{0} are promoted to {1}!",
        "relegated" to "{0} are relegated to {1}.",
        "champion" to "{0} are the {1} champions!",
        "retired" to "{0} ({1}) has announced his retirement.",
        "finance_month" to "Monthly finance report: balance {0}.",
        "scout_report" to "Scouting report: {0} players in {1} are now better known.",
        "manager_sacked" to "{0} has been sacked by {1}.",
        "manager_hired" to "{0} is the new manager of {1}.",
        "renewed" to "{0} signed a new contract until {1}.",
        "ev_goal" to "GOAL! {0} scores for {1}!",
        "ev_penalty_goal" to "GOAL! {0} converts the penalty!",
        "ev_penalty_miss" to "{0} misses the penalty!",
        "ev_save" to "Great save by {0}!",
        "ev_miss" to "{0} shoots, but it's wide.",
        "ev_blocked" to "{0}'s shot is blocked.",
        "ev_yellow" to "Yellow card for {0}.",
        "ev_red" to "Red card! {0} is sent off.",
        "ev_foul" to "Foul by {0}.",
        "ev_offside" to "{0} is caught offside.",
        "ev_corner" to "Corner to {0}.",
        "ev_sub" to "{0} substitution: {1} replaces {2}.",
        "ev_injury" to "{0} is injured and has to come off.",
        "ev_kickoff" to "Kick-off!",
        "ev_halftime" to "Half-time. Score {0}.",
        "ev_fulltime" to "Full-time! Final score {0}.",
        "ev_secondhalf" to "The second half is under way.",
        "ev_chance" to "Big chance for {0}!",
        "ev_tactic" to "{0} change their tactics.",
    )

    fun t(lang: Lang, key: String, args: List<String> = emptyList()): String {
        var s = (if (lang == Lang.ID) id[key] else en[key]) ?: id[key] ?: key
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a) }
        return s
    }

    fun t(lang: Lang, key: String, vararg args: Any): String = t(lang, key, args.map { it.toString() })

    fun hasKey(key: String) = key in id && key in en
}
