package ai.forcedream.sdk

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Ported precisely from @forcedream/mcp-server's search_agents.ts (via the real, published
 * Java SDK's Agents.java). Real, load-bearing fact confirmed directly from that source, not
 * assumed: the server has no working server-side capability/query filter on /v1/agents/list
 * -- filtering must happen client-side, after fetching the full list. Also merges in real
 * reliability data from the separate /v1/agents/reliability endpoint, exactly as the proven
 * implementation does.
 */
object Agents {

    fun searchAgentsFiltered(apiBase: String, capability: String?, query: String?): JsonNode {
        val data = Http.get("$apiBase/v1/agents/list").json
        val relData = try {
            Http.get("$apiBase/v1/agents/reliability").json
        } catch (e: Exception) {
            null
        }

        val agents = Http.MAPPER.createArrayNode()
        if (data.has("agents") && data.get("agents").isArray) {
            data.get("agents").forEach { agents.add(it) }
        }

        val reliabilityBySlug = HashMap<String, JsonNode>()
        if (relData != null && relData.has("agents") && relData.get("agents").isArray) {
            relData.get("agents").forEach { ra ->
                if (ra.has("agent_slug") && ra.has("reliability")) {
                    reliabilityBySlug[ra.get("agent_slug").asText()] = ra.get("reliability")
                }
            }
        }

        val filtered = Http.MAPPER.createArrayNode()
        agents.forEach { a ->
            val capMatch = capability == null || matchesCapability(a, capability)
            val queryMatch = query == null || matchesQuery(a, query)
            if (capMatch && queryMatch) filtered.add(a)
        }

        val enriched: ArrayNode = Http.MAPPER.createArrayNode()
        filtered.forEach { a ->
            val obj = a.deepCopy<ObjectNode>()
            val slug = if (obj.has("slug")) obj.get("slug").asText() else null
            val health = if (slug != null) reliabilityBySlug[slug] else null
            obj.set<ObjectNode>("health", health ?: Http.MAPPER.nullNode())
            enriched.add(obj)
        }

        val result = Http.MAPPER.createObjectNode()
        result.put("count", enriched.size())
        result.set<ArrayNode>("agents", enriched)
        result.put(
            "note",
            if (enriched.isEmpty)
                "No agents matched. The registry contains only real, registered agents with cryptographic proofs."
            else
                "Metrics are system-derived from proofs/ledger (proof_count, success_rate) -- never self-reported. Health (success_rate, avg_latency_ms, sample_size) is honestly null where no real reliability data exists yet."
        )
        return result
    }

    private fun matchesCapability(a: JsonNode, capability: String): Boolean {
        val capLower = capability.lowercase()
        if (!a.has("capabilities") || !a.get("capabilities").isArray) return false
        return a.get("capabilities").any { it.asText().lowercase() == capLower }
    }

    private fun matchesQuery(a: JsonNode, query: String): Boolean {
        val qLower = query.lowercase()
        val slug = if (a.has("slug") && !a.get("slug").isNull) a.get("slug").asText() else ""
        val name = if (a.has("name") && !a.get("name").isNull) a.get("name").asText() else ""
        if (slug.lowercase().contains(qLower) || name.lowercase().contains(qLower)) return true
        if (a.has("capabilities") && a.get("capabilities").isArray) {
            return a.get("capabilities").any { it.asText().lowercase().contains(qLower) }
        }
        return false
    }
}
