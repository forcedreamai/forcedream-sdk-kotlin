package ai.forcedream.sdk.examples

import ai.forcedream.sdk.ForceDream

/**
 * Runs the shared cross-SDK conformance suite against a local mock server.
 * Start forcedream-sdk-conformance/harness/mock_server.py first.
 */
object Conformance {
    @JvmStatic
    fun main(args: Array<String>) {
        // Cases come from the server, never a literal here. A hardcoded list is a
        // snapshot that silently drifts: when the contract gained conf_h and conf_i,
        // every hardcoded harness kept running seven cases and reporting green --
        // validating fixes without ever testing them.
        val cases: List<Pair<String, Boolean>> = try {
            val resp = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://127.0.0.1:8787/conformance/cases")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()
            )
            val root = com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.body())
            root.fields().asSequence()
                .map { it.key to it.value.get("expected").asBoolean() }
                .sortedBy { it.first }.toList()
        } catch (e: Exception) {
            System.err.println("Could not fetch the contract: ${e.message}")
            System.err.println("Start harness/mock_server.py in the conformance repo first.")
            System.exit(2); emptyList()
        }
        if (cases.isEmpty()) {
            System.err.println("INCONCLUSIVE: the server returned no cases.")
            System.exit(2)
        }

        val fd = ForceDream(apiBase = "http://127.0.0.1:8787")
        var passed = 0; var failed = 0; var errored = 0; var verifiedTrue = 0

        for ((id, expected) in cases) {
            try {
                val r = fd.verify(taskId = id)
                if (r.verified) verifiedTrue++
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
        // Most cases expect false, so an unreachable server or an implementation that
        // rejects everything would otherwise report a green partial pass.
        if (verifiedTrue == 0) {
            println("INCONCLUSIVE: no case produced a genuine verified=true.")
            System.exit(2)
        }
        if (failed > 0 || errored > 0) System.exit(1)
    }
}
