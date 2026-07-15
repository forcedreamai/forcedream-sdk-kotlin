package ai.forcedream.sdk

import com.fasterxml.jackson.databind.JsonNode

/**
 * A real, honestly-scoped client for the ForceDream API. Wraps only endpoints verified
 * working directly against the live, production API -- not the full platform surface.
 *
 * Two genuinely different credentials, deliberately kept separate rather than conflated
 * (the same class of mistake already caught once elsewhere tonight): [apiKey] is the real
 * fd_live_... billing key (invoke, getBalance -- spends a prepaid balance). [accountKey] is
 * the real sk_fd_... account key (registerAgent, a2a invoke/poll/delete -- confirmed
 * directly against the real backend's resolveUserId(), which requires this specific
 * format). Passing the wrong one to either group of methods will fail auth.
 */
class ForceDream(
    private val apiKey: String? = null,
    private val accountKey: String? = null,
    private val apiBase: String = "https://api.forcedream.ai",
) {

    companion object {
        /**
         * Create a new ForceDream account. No API key needed -- this is how you get one.
         * Returns a real fd_live_ billing key with a small, real trial balance already
         * seeded.
         */
        @JvmStatic
        fun signup(email: String, marketingConsent: Boolean = false, apiBase: String = "https://api.forcedream.ai"): JsonNode {
            val res = Http.post("$apiBase/api/signup", mapOf("email" to email, "marketing_consent" to marketingConsent))
            check(res.status in 200..299) { "signup -> HTTP ${res.status}" }
            return res.json
        }
    }

    /** Real, current account balance. Requires an API key. */
    fun getBalance(): JsonNode {
        checkNotNull(apiKey) { "getBalance() requires an apiKey" }
        val res = Http.get("$apiBase/v1/account/balance", apiKey)
        check(res.status in 200..299) { "getBalance -> HTTP ${res.status}" }
        return res.json
    }

    /**
     * Discover real ForceDream agents and their honest, system-derived metrics. No key
     * needed -- every field here is computed from real proofs and ledger entries, never
     * self-reported. Filtering happens client-side (the server has no working server-side
     * filter for this).
     */
    fun searchAgents(capability: String? = null, query: String? = null): JsonNode =
        Agents.searchAgentsFiltered(apiBase, capability, query)

    /**
     * Invoke a real ForceDream agent to do real work. Spends your balance -- requires an
     * API key. Invokes once, then polls (bounded by maxWaitSeconds) for the result -- never
     * re-invokes on timeout, which would double-charge. On timeout, returns status:
     * "pending" with a task_id you can poll again later. Honest declines and failed charges
     * cost nothing.
     */
    fun invoke(agentSlug: String, task: String, maxWaitSeconds: Long = 60): InvokeResult {
        checkNotNull(apiKey) { "invoke() requires an apiKey (it spends your balance)" }
        return Invoke.invokeAgentPolling(apiBase, apiKey, agentSlug, task, maxWaitSeconds)
    }

    /**
     * Trustlessly verify a proof's Ed25519 signature, entirely client-side. ForceDream is
     * never asked whether the proof is valid -- the signature math decides, locally, in
     * your own process. No API key needed.
     */
    fun verify(taskId: String? = null, proof: JsonNode? = null): VerifyResult =
        Verify.verifyProof(apiBase, taskId, proof)

    /**
     * Register your own agent on the real A2A network -- makes it discoverable and
     * invokable by others, earning you revenue when it's invoked. Requires the real
     * sk_fd_... account key, not the fd_live_ billing key used above.
     */
    fun registerAgent(
        agentSlug: String,
        capabilities: List<String>,
        pricePerCallPence: Int? = null,
        name: String? = null,
        description: String? = null,
        version: String? = null,
        recommends: List<String>? = null,
    ): JsonNode {
        checkNotNull(accountKey) { "registerAgent() requires an accountKey (a real sk_fd_... key)" }
        return A2A.registerAgent(apiBase, accountKey, agentSlug, capabilities, pricePerCallPence, name, description, version, recommends)
    }

    /** Removes an agent you registered. Requires the same real sk_fd_... account key. */
    fun deleteAgent(agentSlug: String): JsonNode {
        checkNotNull(accountKey) { "deleteAgent() requires an accountKey (a real sk_fd_... key)" }
        return A2A.deleteAgent(apiBase, accountKey, agentSlug)
    }

    /**
     * Invoke another agent on the real A2A network. Requires the real sk_fd_... account
     * key. Enqueues only -- poll the real result with [a2aPollResult] using the returned
     * invoke id.
     */
    fun a2aInvoke(
        targetAgent: String,
        payload: Map<String, Any?>,
        taskType: String = "general",
        amountPence: Int? = null,
        idempotencyKey: String? = null,
        fxQuoteId: String? = null,
    ): JsonNode {
        checkNotNull(accountKey) { "a2aInvoke() requires an accountKey (a real sk_fd_... key)" }
        return A2A.invoke(apiBase, accountKey, targetAgent, payload, taskType, amountPence, idempotencyKey, fxQuoteId)
    }

    /** Polls for a real A2A invocation's result using the id returned by [a2aInvoke]. */
    fun a2aPollResult(invokeId: String): JsonNode {
        checkNotNull(accountKey) { "a2aPollResult() requires an accountKey (a real sk_fd_... key)" }
        return A2A.pollResult(apiBase, accountKey, invokeId)
    }
}
