package ai.forcedream.sdk

import com.fasterxml.jackson.databind.JsonNode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class InvokeResult(
    val status: String,
    val agent: String,
    val taskId: String? = null,
    val output: JsonNode? = null,
    val chargedPence: Long? = null,
    val proofId: String? = null,
    val message: String,
    val error: String? = null,
)

/**
 * Ported precisely from @forcedream/mcp-server's invoke_agent.ts (via the real, published
 * Java SDK's Invoke.java) -- exact endpoints, exact polling interval ramp (starts 2500ms,
 * +1000ms per attempt, capped at 6000ms), exact status handling. Invokes ONCE; never
 * re-invokes on timeout (would double-charge) -- returns a pollable task_id instead.
 */
object Invoke {

    fun invokeAgentPolling(apiBase: String, apiKey: String, agentSlug: String, task: String, maxWaitSeconds: Long?): InvokeResult {
        val slug = agentSlug
        val maxWaitMs = (maxWaitSeconds ?: 60).coerceIn(5, 120) * 1000L
        val encodedSlug = URLEncoder.encode(slug, StandardCharsets.UTF_8)

        try {
            val inv = Http.post("$apiBase/v1/agents/$encodedSlug/invoke", mapOf("task" to task), apiKey)

            if (inv.status == 401) {
                return InvokeResult("error", slug, message = "Invalid API key (401).", error = "invalid_key")
            }

            val invJson = inv.json
            if (!invJson.has("task_id") || invJson.get("task_id").isNull) {
                val errMsg = when {
                    invJson.has("error") -> invJson.get("error").asText()
                    invJson.has("note") -> invJson.get("note").asText()
                    else -> "no task_id"
                }
                return InvokeResult("error", slug, message = "Invoke failed (HTTP ${inv.status}): $errMsg", error = "invoke_failed")
            }

            val taskId = invJson.get("task_id").asText()
            val start = System.currentTimeMillis()
            var intervalMs = 2500L

            while (System.currentTimeMillis() - start < maxWaitMs) {
                Thread.sleep(intervalMs)

                val encodedTaskId = URLEncoder.encode(taskId, StandardCharsets.UTF_8)
                val poll = Http.get("$apiBase/v1/agents/$encodedSlug/result/$encodedTaskId", apiKey)
                val d = poll.json

                val status = when {
                    d.has("status") -> d.get("status").asText()
                    d.has("outcome") -> d.get("outcome").asText()
                    else -> ""
                }
                val okTrue = d.has("ok") && d.get("ok").asBoolean(false)

                if (status == "completed" || status == "succeeded" || okTrue) {
                    val output = d.get("output")
                    val isInsufficient = (d.has("outcome") && d.get("outcome").asText() == "insufficient") ||
                        (output != null && output.has("confidence") && output.get("confidence").asText() == "insufficient")

                    if (isInsufficient) {
                        return InvokeResult(
                            "insufficient", slug, taskId, output, 0L,
                            message = "Agent returned insufficient evidence and declined rather than fabricate. Charged nothing."
                        )
                    }

                    val charged = if (d.has("charged_pence")) d.get("charged_pence").asLong() else null
                    val proofId = if (d.has("proof_id")) d.get("proof_id").asText() else taskId
                    return InvokeResult(
                        "completed", slug, taskId, output, charged, proofId,
                        "Completed. Charged ${charged ?: 0}p. Cryptographically proven (proof_id $proofId)."
                    )
                }

                if (status == "insufficient") {
                    return InvokeResult("insufficient", slug, taskId, d.get("output"), 0L, message = "Agent declined (insufficient evidence). Charged nothing.")
                }

                if (status == "charge_failed") {
                    val reason = if (d.has("reason")) d.get("reason").asText() else "insufficient_balance"
                    return InvokeResult("error", slug, taskId, chargedPence = 0L, message = "Charge failed: $reason. Nothing charged or delivered. Top up and retry.", error = "charge_failed")
                }

                if (status == "failed" || status == "dead_letter") {
                    val reason = when {
                        d.has("reason") -> d.get("reason").asText()
                        d.has("last_error") -> d.get("last_error").asText()
                        else -> "unknown"
                    }
                    return InvokeResult("error", slug, taskId, message = "Task $status: $reason", error = status)
                }

                intervalMs = (intervalMs + 1000).coerceAtMost(6000)
            }

            return InvokeResult(
                "pending", slug, taskId,
                message = "Still processing after ${maxWaitMs / 1000}s. Not re-invoked (would double-charge). Poll the result later with this task_id."
            )
        } catch (e: Exception) {
            return InvokeResult("error", slug, message = "Invoke request failed: ${e.message}", error = "request_failed")
        }
    }
}
