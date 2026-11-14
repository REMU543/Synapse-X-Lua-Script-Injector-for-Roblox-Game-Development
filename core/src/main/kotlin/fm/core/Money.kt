package fm.core

/**
 * Money is always a Long amount of Rupiah. No floating point is used for balances,
 * so finances are exact, deterministic and auditable.
 */
object Money {
    const val JUTA = 1_000_000L
    const val MILIAR = 1_000_000_000L

    fun format(amount: Long, lang: Lang = Lang.ID): String {
        val neg = amount < 0
        val a = kotlin.math.abs(amount.toDouble())
        val s = when {
            a >= 1e12 -> String.format(java.util.Locale.ROOT, "%.2f %s", a / 1e12, if (lang == Lang.ID) "T" else "T")
            a >= 1e9 -> String.format(java.util.Locale.ROOT, "%.1f %s", a / 1e9, if (lang == Lang.ID) "M" else "B")
            a >= 1e6 -> String.format(java.util.Locale.ROOT, "%.0f %s", a / 1e6, if (lang == Lang.ID) "jt" else "M")
            else -> String.format(java.util.Locale.ROOT, "%.0f", a)
        }
        return (if (neg) "-" else "") + "Rp " + s
    }

    /** Multiply money by a ratio expressed as a Double, rounding to whole Rupiah. */
    fun scale(amount: Long, factor: Double): Long = Math.round(amount * factor)
}
