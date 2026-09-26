package com.tandemmoto.playlist

/**
 * Sortable position keys: [between] always finds a key strictly between two others, so a song can
 * be moved or added anywhere without renumbering the rest. Keys use the 62 characters 0-9A-Za-z
 * (in ASCII order, so plain string comparison sorts them) and never end in '0', which keeps a key
 * below any given one always available.
 */
object FractionalIndex {
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    /** A key after [before] (or the first key) and before [after] (or anything). */
    fun between(before: String?, after: String?): String {
        val a = before.orEmpty()
        require(after == null || a < after) { "Keys out of order" }
        return midpoint(a, after)
    }

    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            // Keep the shared prefix, then find a midpoint of what's left.
            var n = 0
            while (n < b.length && (a.getOrNull(n) ?: '0') == b[n]) n++
            if (n > 0) return b.substring(0, n) + midpoint(a.drop(n), b.substring(n))
        }
        val da = if (a.isEmpty()) 0 else digit(a[0])
        val db = if (b == null) BASE else digit(b[0])
        return if (db - da > 1) {
            DIGITS[(da + db) / 2].toString()
        } else if (b != null && b.length > 1) {
            // Adjacent first digits: b's first digit alone is between a and b.
            b.substring(0, 1)
        } else {
            DIGITS[da] + midpoint(a.drop(1), null)
        }
    }

    private fun digit(c: Char): Int = DIGITS.indexOf(c).also { require(it >= 0) { "Bad key" } }
}
