package ai.forcedream.sdk.examples

import ai.forcedream.sdk.ForceDream

/**
 * Runs the shared cross-SDK conformance suite against a local mock server.
 * Start forcedream-sdk-conformance/harness/mock_server.py first.
 */
object Conformance {
    @JvmStatic
    fun main(args: Array<String>) {
        val cases = listOf(
            "conf_a_real_batched" to true,
            "conf_b_real_batched" to true,
            "conf_c_bad_signature" to false,
            "conf_d_bad_payload" to false,
            "conf_e_bad_algorithm" to false,
            "conf_f_siblings_wrong_root" to false,
            "conf_g_missing_root" to false,
        )

        val fd = ForceDream(apiBase = "http://127.0.0.1:8787")
        var passed = 0; var failed = 0; var errored = 0

        for ((id, expected) in cases) {
            try {
                val r = fd.verify(taskId = id)
                if (r.verified == expected) {
                    println("  PASS  ${id.padEnd(32)} verified=${r.verified}"); passed++
                } else {
                    println("  FAIL  ${id.padEnd(32)} expected=$expected got=${r.verified}"); failed++
                }
            } catch (e: Exception) {
                println("  ERROR ${id.padEnd(32)} ${e.javaClass.simpleName}: ${e.message}"); errored++
            }
        }

        println("\n$passed/${cases.size} passed, $failed failed, $errored threw")
        if (failed > 0 || errored > 0) System.exit(1)
    }
}
