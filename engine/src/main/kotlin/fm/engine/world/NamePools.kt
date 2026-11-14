package fm.engine.world

/**
 * Name pools for procedural people and clubs. Names are common given names / family names
 * combined at random, so generated people are fictional. Club names use neutral patterns
 * (no real club names or trademarks).
 */
object NamePools {
    class Pool(val first: List<String>, val last: List<String>, val singleNameChance: Double = 0.0, val familyFirst: Boolean = false)

    private fun l(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val pools: Map<String, Pool> = mapOf(
        "id" to Pool(
            l("Ahmad,Andi,Arif,Bagas,Bayu,Budi,Dedi,Dimas,Eka,Fajar,Fikri,Gilang,Hendra,Ilham,Irfan,Joko,Kevin,Rizky,Lutfi,Muhammad,Nanda,Oki,Rafi,Reza,Rian,Satria,Taufik,Teguh,Wahyu,Yoga,Yudha,Zulfikar,Aditya,Agung,Bima,Dwi,Egi,Ferdi,Hanif,Ikhsan,Jefri,Kadek,Made,Nyoman,Wayan,Rendi,Sandi,Galih,Haris,Iqbal,Rahmat,Robby,Septian,Toni,Yanto,Zaenal,Ardi,Bintang,Dani,Erik"),
            l("Saputra,Pratama,Wijaya,Hidayat,Nugroho,Santoso,Setiawan,Kurniawan,Siregar,Nasution,Harahap,Lubis,Simanjuntak,Sitompul,Pangaribuan,Rumbiak,Wanggai,Mandowen,Kogoya,Sembiring,Ginting,Tarigan,Hutapea,Ramadhan,Firmansyah,Maulana,Syahputra,Gunawan,Susanto,Purnomo,Wibowo,Hakim,Fauzi,Rahman,Sulistyo,Haryanto,Utomo,Permana,Hermawan,Suryadi,Lestaluhu,Tuasalamony,Ohee,Mofu,Pattinasarany,Situmorang,Manurung,Sihombing,Pane,Daulay"),
            singleNameChance = 0.07),
        "my" to Pool(
            l("Muhammad,Ahmad,Mohd,Faisal,Hafiz,Syafiq,Amirul,Azri,Haziq,Shahrul,Ridzuan,Nazmi,Akhyar,Danial,Aiman,Irfan,Khairul,Hakimi,Arif,Firdaus,Izzat,Luqman,Nabil,Qayyum,Syazwan"),
            l("Rahman,Ismail,Hassan,Yusof,Ibrahim,Osman,Kamal,Aziz,Zainal,Razak,Salleh,Hamid,Ghani,Rashid,Latif,Jaafar,Nordin,Sulaiman,Mokhtar,Bakar,Halim,Rosli,Zakaria,Shukor")),
        "th" to Pool(
            l("Somchai,Anon,Kittipong,Prasit,Sarawut,Thanawat,Nattapong,Pakorn,Supachai,Wichai,Apichart,Jakkrit,Kritsada,Narong,Phanuphong,Sittichok,Tanakorn,Weerawut,Chalerm,Ekkachai,Pichit,Surasak,Thitipan,Wutthichai"),
            l("Srisuk,Chaiyaporn,Wongsa,Boonmee,Saetang,Phromma,Kaewkla,Sombat,Thongchai,Rattanakul,Inthasorn,Chaiwong,Saelim,Sukprasert,Pongsri,Kongkaew,Jaidee,Somboon,Siripong,Thammasat,Kaewmanee,Prasertsuk")),
        "vn" to Pool(
            l("Nguyen,Tran,Le,Pham,Hoang,Phan,Vu,Dang,Bui,Do,Ho,Ngo,Duong,Ly,Truong,Dinh"),
            l("Van Anh,Duc Huy,Minh Tuan,Van Toan,Hoang Duc,Thanh Binh,Van Thanh,Duy Manh,Tuan Anh,Huu Thang,Van Lam,Quoc Viet,Dinh Trong,Tan Tai,Hung Dung,Viet Hung,Minh Khoa,Thanh Long,Van Hau,Quang Vinh,Gia Bao,Thai Son"),
            familyFirst = true),
        "jp" to Pool(
            l("Haruto,Ren,Yuto,Sota,Kaito,Riku,Daiki,Shota,Takumi,Kenta,Ryo,Hiroki,Yuki,Kazuki,Naoki,Shun,Tatsuya,Kenji,Taiga,Sho,Kosuke,Masato,Yusuke,Tomoya"),
            l("Sato,Suzuki,Takahashi,Tanaka,Watanabe,Ito,Yamamoto,Nakamura,Kobayashi,Kato,Yoshida,Yamada,Sasaki,Yamaguchi,Matsumoto,Inoue,Kimura,Hayashi,Shimizu,Mori,Ikeda,Hashimoto,Ishikawa,Ogawa,Fujita,Okada")),
        "br" to Pool(
            l("Gabriel,Lucas,Matheus,Rafael,Thiago,Bruno,Felipe,Gustavo,Diego,Rodrigo,Caio,Leandro,Marcelo,Wesley,Everton,Renan,Igor,Danilo,Fabio,Joao,Pedro,Vitor,Henrique,Murilo"),
            l("Silva,Santos,Oliveira,Souza,Pereira,Costa,Ferreira,Almeida,Lima,Carvalho,Gomes,Ribeiro,Rocha,Martins,Barbosa,Araujo,Mendes,Moraes,Nascimento,Teixeira,Cardoso,Correia")),
        "kr" to Pool(
            l("Min-jun,Seo-jun,Ji-ho,Hyun-woo,Jun-seo,Do-yun,Ji-hoon,Sung-min,Dong-hyun,Jae-won,Tae-hee,Woo-jin,Sang-ho,Young-jae,Kyung-soo,Jin-woo"),
            l("Kim,Lee,Park,Choi,Jung,Kang,Cho,Yoon,Jang,Lim,Han,Oh,Seo,Shin,Kwon,Hwang")),
        "au" to Pool(
            l("Jack,James,Liam,Noah,Oliver,Thomas,Lachlan,Mitchell,Ryan,Daniel,Joshua,Harry,Connor,Jordan,Nathan,Luke,Cameron,Riley,Brodie,Callum"),
            l("Smith,Jones,Williams,Brown,Wilson,Taylor,Johnson,White,Martin,Anderson,Thompson,Walker,Harris,Mitchell,Kelly,Murphy,Clarke,Robinson,Wright,Hughes")),
        "es" to Pool(
            l("Javier,Alejandro,Pablo,Sergio,Adrian,Alvaro,David,Daniel,Marcos,Ivan,Ruben,Hugo,Mario,Jorge,Carlos,Raul,Iker,Unai,Gonzalo,Victor"),
            l("Garcia,Fernandez,Gonzalez,Rodriguez,Lopez,Martinez,Sanchez,Perez,Gomez,Martin,Jimenez,Ruiz,Hernandez,Diaz,Moreno,Alvarez,Romero,Navarro,Torres,Dominguez")),
        "ng" to Pool(
            l("Chinedu,Emeka,Oluwaseun,Tunde,Ikechukwu,Uche,Chukwuemeka,Babatunde,Femi,Kelechi,Sunday,Samuel,Victor,Peter,Ahmed,Godwin,Emmanuel,Daniel"),
            l("Okafor,Adeyemi,Okonkwo,Nwosu,Balogun,Eze,Okeke,Adebayo,Ibrahim,Musa,Obi,Uzor,Ogunleye,Chukwu,Bello,Afolabi,Onyeka,Danjuma")),
    )

    fun pool(key: String) = pools[key] ?: pools.getValue("id")

    val cities: Map<String, List<String>> = mapOf(
        "IDN" to l("Jakarta,Bandung,Surabaya,Malang,Semarang,Yogyakarta,Solo,Makassar,Medan,Palembang,Padang,Pekanbaru,Balikpapan,Samarinda,Banjarmasin,Pontianak,Manado,Denpasar,Mataram,Kupang,Jayapura,Ambon,Bogor,Bekasi,Tangerang,Serang,Cirebon,Tasikmalaya,Kediri,Madiun,Sidoarjo,Gresik,Lamongan,Bojonegoro,Jember,Banyuwangi,Purwokerto,Tegal,Kudus,Pati,Magelang,Sleman,Bantul,Klaten,Batam,Jambi,Bengkulu,Lampung,Banda Aceh,Sorong,Ternate,Palu,Kendari,Gorontalo,Tarakan,Pangkalpinang,Cilegon,Sukabumi"),
        "MAS" to l("Kuala Lumpur,Johor Bahru,Penang,Ipoh,Kuching,Kota Kinabalu,Melaka,Seremban,Alor Setar,Kuantan,Kuala Terengganu,Kota Bharu,Shah Alam,Petaling Jaya,Miri,Sandakan"),
        "THA" to l("Bangkok,Chiang Mai,Khon Kaen,Nakhon Ratchasima,Chonburi,Rayong,Udon Thani,Pattaya,Phuket,Hat Yai,Ubon Ratchathani,Lampang,Sukhothai,Ayutthaya,Nonthaburi,Surat Thani,Phitsanulok"),
        "VIE" to l("Hanoi,Ho Chi Minh,Hai Phong,Da Nang,Nam Dinh,Thanh Hoa,Nghe An,Hue,Quang Ninh,Binh Duong,Khanh Hoa,Can Tho,Dong Thap,Gia Lai,Vung Tau,Long An"),
        "JPN" to l("Tokyo,Osaka,Yokohama,Nagoya,Sapporo,Fukuoka,Kobe,Kyoto,Sendai,Hiroshima,Niigata,Shizuoka,Kawasaki,Saitama,Chiba,Kumamoto,Okayama,Nagasaki,Kanazawa,Matsuyama"),
    )

    val clubPatterns: Map<String, List<String>> = mapOf(
        "IDN" to listOf("{c} FC", "Bintang {c}", "Laskar {c}", "Garuda {c}", "{c} Raya", "Putra {c}", "Satria {c}", "Elang {c}", "Sinar {c}", "Rajawali {c}", "{c} Jaya", "Harimau {c}"),
        "MAS" to listOf("{c} FC", "{c} City", "{c} Athletic", "Harimau {c}", "{c} Rangers"),
        "THA" to listOf("{c} FC", "{c} City", "{c} Athletic", "{c} Warriors", "{c} Rovers"),
        "VIE" to listOf("{c} FC", "{c} Club", "Sporting {c}", "{c} Stars", "{c} City"),
        "JPN" to listOf("{c} FC", "{c} Athletic", "Sporting {c}", "{c} Stars", "{c} Rovers", "{c} City"),
    )

    val stadiumPatterns = listOf("Stadion {c}", "Stadion Utama {c}", "Gelora {c}", "Arena {c}", "Stadion Kota {c}")

    val injuries = listOf(
        "Cedera hamstring" to 14, "Keseleo pergelangan kaki" to 10, "Memar paha" to 5,
        "Cedera lutut ringan" to 21, "Cedera betis" to 12, "Cedera pangkal paha" to 18,
        "Robek ligamen lutut" to 180, "Patah tulang kaki" to 120, "Cedera bahu" to 28,
        "Gegar otak ringan" to 7, "Cedera punggung" to 9, "Cedera lutut (meniskus)" to 60,
    )
}
