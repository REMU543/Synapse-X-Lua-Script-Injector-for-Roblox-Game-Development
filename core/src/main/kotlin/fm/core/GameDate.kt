package fm.core

import java.time.LocalDate

/** Game calendar helpers. Dates are stored as epoch days (Int) for compact, exact saves. */
object Cal {
    fun of(year: Int, month: Int, day: Int): Int = LocalDate.of(year, month, day).toEpochDay().toInt()
    fun date(epochDay: Int): LocalDate = LocalDate.ofEpochDay(epochDay.toLong())
    fun year(epochDay: Int) = date(epochDay).year
    fun month(epochDay: Int) = date(epochDay).monthValue
    fun dayOfMonth(epochDay: Int) = date(epochDay).dayOfMonth
    /** 1 = Monday … 7 = Sunday. */
    fun dayOfWeek(epochDay: Int) = date(epochDay).dayOfWeek.value

    /** First day on or after [epochDay] that falls on [dow] (1 = Monday). */
    fun nextDow(epochDay: Int, dow: Int): Int {
        var d = epochDay
        while (dayOfWeek(d) != dow) d++
        return d
    }

    /** Whole years between [birth] and [on]. */
    fun age(birth: Int, on: Int): Int {
        val b = date(birth); val o = date(on)
        var a = o.year - b.year
        if (o.monthValue < b.monthValue || (o.monthValue == b.monthValue && o.dayOfMonth < b.dayOfMonth)) a--
        return a
    }

    /** Age as a fractional number of years. */
    fun ageExact(birth: Int, on: Int): Double = (on - birth) / 365.25

    private val monthsId = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    private val monthsEn = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val daysId = arrayOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    private val daysEn = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun format(epochDay: Int, lang: Lang = Lang.ID): String {
        val d = date(epochDay)
        val m = if (lang == Lang.ID) monthsId else monthsEn
        val w = if (lang == Lang.ID) daysId else daysEn
        return "${w[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${m[d.monthValue - 1]} ${d.year}"
    }

    fun formatShort(epochDay: Int, lang: Lang = Lang.ID): String {
        val d = date(epochDay)
        val m = if (lang == Lang.ID) monthsId else monthsEn
        return "${d.dayOfMonth} ${m[d.monthValue - 1]}"
    }
}
