package ai.forcedream.sdk

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.TreeMap

/**
 * Exact replica of the server's wfCanonical: JSON.stringify(obj, Object.keys(obj).sort()).
 * Sorted keys, no whitespace. Faithfully ported from the real, published Java SDK's
 * Canonical.java (which is itself a verbatim port of the server's own canonical.ts) --
 * not rewritten from memory for this new language.
 *
 * Uses a custom, minimal serializer rather than a general-purpose JSON library for this
 * specific step, since exact byte-for-byte output matters here (a single differing byte
 * changes the signed bytes and breaks every signature check).
 */
object Canonical {

    fun wfCanonical(obj: Map<String, Any?>): String {
        val sorted = TreeMap(obj)
        val sb = StringBuilder("{")
        var first = true
        for ((key, value) in sorted) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(escape(key)).append("\":")
            sb.append(serializeValue(value))
        }
        sb.append('}')
        return sb.toString()
    }

    private fun serializeValue(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"${escape(v)}\""
        is Number -> jsNumber(v.toDouble())
        is Boolean -> v.toString()
        else -> throw IllegalArgumentException("Unsupported type for canonicalization: ${v::class}")
    }

    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Mirrors JS's Number(x) -> JSON.stringify() behavior: whole values with no decimal
     * point, fractional values preserved, never scientific notation. Confirmed directly
     * (not assumed) that Kotlin's Double.toString() has the exact same scientific-notation
     * bug above ~10^7 as Java's (Kotlin's Double maps directly to the JVM's double, sharing
     * the same underlying formatting) -- the same class of bug that required a fix in every
     * other language SDK tonight, ported here via the same proven BigDecimal approach as the
     * real Java SDK, not re-derived independently.
     */
    fun jsNumber(d: Double): String {
        val bd = BigDecimal.valueOf(d)
        val stripped = bd.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            stripped.toPlainString()
        }
    }

    fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(s.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
