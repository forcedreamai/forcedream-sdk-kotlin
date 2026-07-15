package ai.forcedream.sdk.examples

import ai.forcedream.sdk.ForceDream

fun main() {
    println("=== Real signup ===")
    val signup = ForceDream.signup("kotlin-sdk-test-${System.currentTimeMillis()}@example.com")
    println("Signed up: user_id=${signup["user_id"].asText()}, trial_balance=${signup["trial_balance_gbp"].asText()}")

    val client = ForceDream(signup["live_key"].asText())

    println("\n=== searchAgents (client-side filtered) ===")
    val results = client.searchAgents(query = "extract")
    println(results.toPrettyString())

    println("\n=== invoke (real agent, real charge) ===")
    val invokeResult = client.invoke(
        "data-extract-v1",
        "Extract year and location from: The exhibition opened in Tokyo in 2011.",
        60
    )
    println(invokeResult)

    println("\n=== verify (real Ed25519 proof) ===")
    val taskId = invokeResult.taskId
    if (taskId != null) {
        val verifyResult = client.verify(taskId = taskId)
        println(verifyResult)
    } else {
        println("No task_id to verify.")
    }

    println("\n=== A2A: register a real agent (uses the sk_fd_ account key, not the fd_live_ one) ===")
    val a2aClient = ForceDream(accountKey = signup["api_key"].asText())
    val slug = "kotlin-sdk-test-agent-${System.currentTimeMillis()}"
    val registerResult = a2aClient.registerAgent(
        agentSlug = slug,
        capabilities = listOf("data:extraction"),
        pricePerCallPence = 5,
        name = "Kotlin SDK Test Agent",
        description = "A real, temporary agent registered by the Kotlin SDK's own live test.",
    )
    println(registerResult.toPrettyString())

    println("\n=== A2A: clean up -- delete the just-registered test agent ===")
    val deleteResult = a2aClient.deleteAgent(slug)
    println(deleteResult.toPrettyString())
}
