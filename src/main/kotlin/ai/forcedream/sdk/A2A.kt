package ai.forcedream.sdk

import com.fasterxml.jackson.databind.JsonNode

/**
 * Real A2A (agent-to-agent) bindings -- lets a developer register their own agent on the
 * real A2A network (making it discoverable and invokable by others, earning them revenue
 * when invoked) and invoke other registered agents. Endpoint request/response shapes
 * confirmed directly against the real backend source (api/server.ts) before writing this,
 * not assumed or reconstructed from a description.
 *
 * Uses a real, different credential from FD_LIVE_KEY/invoke(): these four endpoints all
 * authenticate via resolveUserId(), which requires an sk_fd_... account key specifically --
 * confirmed directly, not assumed (the same class of key-type mismatch already caught and
 * fixed once elsewhere tonight). Passing an fd_live_ key here will fail auth.
 */
object A2A {

    fun registerAgent(
        apiBase: String,
        accountKey: String,
        agentSlug: String,
        capabilities: List<String>,
        pricePerCallPence: Int? = null,
        name: String? = null,
        description: String? = null,
        version: String? = null,
        recommends: List<String>? = null,
    ): JsonNode {
        val body = LinkedHashMap<String, Any?>()
        body["agent_slug"] = agentSlug
        body["capabilities"] = capabilities
        if (pricePerCallPence != null) body["price_per_call_pence"] = pricePerCallPence
        if (name != null) body["name"] = name
        if (description != null) body["description"] = description
        if (version != null) body["version"] = version
        if (recommends != null) body["recommends"] = recommends

        val res = Http.post("$apiBase/v1/a2a/register-agent", body, accountKey)
        return res.json
    }

    fun deleteAgent(apiBase: String, accountKey: String, agentSlug: String): JsonNode {
        val res = Http.post("$apiBase/v1/a2a/delete-agent", mapOf("agent_slug" to agentSlug), accountKey)
        return res.json
    }

    fun invoke(
        apiBase: String,
        accountKey: String,
        targetAgent: String,
        payload: Map<String, Any?>,
        taskType: String = "general",
        amountPence: Int? = null,
        idempotencyKey: String? = null,
        fxQuoteId: String? = null,
    ): JsonNode {
        val body = LinkedHashMap<String, Any?>()
        body["target_agent"] = targetAgent
        body["payload"] = payload
        body["task_type"] = taskType
        if (amountPence != null) body["amount_pence"] = amountPence
        if (idempotencyKey != null) body["idempotency_key"] = idempotencyKey
        if (fxQuoteId != null) body["fx_quote_id"] = fxQuoteId

        val res = Http.post("$apiBase/v1/a2a/invoke", body, accountKey)
        return res.json
    }

    fun pollResult(apiBase: String, accountKey: String, invokeId: String): JsonNode {
        val res = Http.get("$apiBase/v1/a2a/result/$invokeId", accountKey)
        return res.json
    }
}
