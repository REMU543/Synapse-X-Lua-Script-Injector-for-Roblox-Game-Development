package fm.core

/**
 * Deterministic pseudo-random generator (xoshiro256**, seeded with SplitMix64).
 *
 * The game never uses kotlin.random / java.util.Random: every random decision draws from
 * a stream derived from (worldSeed, domain, keys...), so one system's randomness can never
 * shift another's, and the same seed plus the same user inputs reproduces the same world.
 */
class Rng(seed: Long) {
    private var s0: Long
    private var s1: Long
    private var s2: Long
    private var s3: Long

    init {
        var x = seed
        fun sm(): Long {
            x += -0x61c8864680b583ebL
            var z = x
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
        s0 = sm(); s1 = sm(); s2 = sm(); s3 = sm()
    }

    fun nextLong(): Long {
        val result = java.lang.Long.rotateLeft(s1 * 5, 7) * 9
        val t = s1 shl 17
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = java.lang.Long.rotateLeft(s3, 45)
        return result
    }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 33) % bound).toInt()
    }

    /** Uniform integer in [from, to] inclusive. */
    fun range(from: Int, to: Int): Int = if (to <= from) from else from + nextInt(to - from + 1)

    fun range(from: Double, to: Double): Double = from + nextDouble() * (to - from)

    fun chance(p: Double): Boolean = nextDouble() < p

    /** Standard normal sample (Box–Muller). */
    fun gaussian(): Double {
        var u = nextDouble()
        if (u < 1e-12) u = 1e-12
        val v = nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2.0 * Math.PI * v)
    }

    fun gaussian(mean: Double, sd: Double): Double = mean + sd * gaussian()

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]

    fun <T> weighted(list: List<T>, weight: (T) -> Double): T {
        var total = 0.0
        for (e in list) total += maxOf(0.0, weight(e))
        if (total <= 0.0) return pick(list)
        var r = nextDouble() * total
        for (e in list) {
            r -= maxOf(0.0, weight(e))
            if (r <= 0.0) return e
        }
        return list.last()
    }

    fun <T> shuffled(list: List<T>): List<T> {
        val out = list.toMutableList()
        for (i in out.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = out[i]; out[i] = out[j]; out[j] = tmp
        }
        return out
    }

    /** Poisson sample (Knuth), fine for the small means used in football. */
    fun poisson(mean: Double): Int {
        val l = kotlin.math.exp(-mean)
        var k = 0
        var p = 1.0
        do { k++; p *= nextDouble() } while (p > l)
        return k - 1
    }

    companion object {
        /** Stable 64-bit FNV-1a hash of a string (used for domain names). */
        fun hash(s: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in s) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
            return h
        }

        private fun mix(a: Long, b: Long): Long {
            var z = a xor (b + -0x61c8864680b583ebL + (a shl 6) + (a ushr 2))
            z = (z xor (z ushr 33)) * -0xae502812aa7333L
            z = (z xor (z ushr 33)) * -0x3b314601e57a13adL
            return z xor (z ushr 33)
        }

        /** Derive an independent stream for a domain and any number of integer keys. */
        fun stream(worldSeed: Long, domain: String, vararg keys: Long): Rng {
            var s = mix(worldSeed, hash(domain))
            for (k in keys) s = mix(s, k)
            return Rng(s)
        }
    }
}
