package ai.forcedream.sdk

import com.fasterxml.jackson.databind.JsonNode
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.TreeMap

data class VerifyResult(
    val verified: Boolean,
    val taskId: String?,
    val keyId: String?,
    val algorithm: String,
    val fieldsSigned: Int,
    val trustless: Boolean,
    val message: String,
)

/**
 * Trustlessly verifies a ForceDream proof's Ed25519 signature entirely client-side.
 * ForceDream is never asked whether the proof is valid -- the math decides, locally.
 * Faithfully ported from the real, published Java SDK's Verify.java, itself a verbatim port
 * of the server's own logic -- not reconstructed from a description for this new language.
 *
 * Uses Java's native Ed25519 support (java.security, built in since Java 15) directly via
 * Kotlin's JVM interop -- confirmed by the same, already-proven Java implementation; no
 * external crypto dependency needed. Unlike PHP's sodium (which needs the raw 32-byte key),
 * KeyFactory.generatePublic() with X509EncodedKeySpec parses the full SPKI DER structure
 * itself -- no manual byte-offset extraction needed here at all.
 */
object Verify {

    private data class Signable(val fields: Map<String, Any?>, val fieldCount: Int)

    private fun buildSignable(p: JsonNode): Signable {
        val hasExt = p.has("external_cost_hash") && !p.get("external_cost_hash").isNull

        val base = TreeMap<String, Any?>()
        base["task_id"] = textOrNull(p, "task_id")
        base["agent_id"] = textOrNull(p, "agent_id")
        base["input_hash"] = textOrNull(p, "input_hash")
        base["output_hash"] = textOrNull(p, "output_hash")
        base["cost_pence"] = jsNumberValue(p, "cost_pence")
        base["budget_pence"] = jsNumberValue(p, "budget_pence")
        base["started_at"] = jsNumberValue(p, "started_at")
        base["completed_at"] = jsStringValue(p, "completed_at")

        return if (hasExt) {
            base["external_cost_hash"] = jsStringValue(p, "external_cost_hash")
            base["retrieved_count"] = if (p.has("retrieved_count")) jsNumberValue(p, "retrieved_count") else 0.0
            Signable(base, 10)
        } else {
            Signable(base, 8)
        }
    }

    private fun textOrNull(p: JsonNode, field: String): String? =
        if (p.has(field) && !p.get(field).isNull) p.get(field).asText() else null

    private fun jsNumberValue(p: JsonNode, field: String): Double {
        if (!p.has(field) || p.get(field).isNull) return 0.0
        val v = p.get(field)
        return if (v.isTextual) v.asText().toDouble() else v.asDouble()
    }

    private fun jsStringValue(p: JsonNode, field: String): String {
        if (!p.has(field) || p.get(field).isNull) return ""
        val v = p.get(field)
        return if (v.isTextual) v.asText() else Canonical.jsNumber(v.asDouble())
    }

    fun verifyProof(apiBase: String, taskId: String?, proofInput: JsonNode?): VerifyResult {
        val proof: JsonNode = proofInput ?: run {
            requireNotNull(taskId) { "Provide task_id or proof" }
            val data = Http.get("$apiBase/v1/workforce/proof/$taskId/public").json
            check(data.has("proof") && !data.get("proof").isNull) { "proof_not_found" }
            data.get("proof")
        }

        val keyData = Http.get("$apiBase/v1/workforce/proof/public-key").json
        val keyId = if (keyData.has("key_id")) keyData.get("key_id").asText() else null
        val pem = if (keyData.has("public_key_pem")) keyData.get("public_key_pem").asText() else ""

        val verifyingKey: PublicKey? = try {
            val der = pemToDer(pem)
            val kf = KeyFactory.getInstance("Ed25519")
            kf.generatePublic(X509EncodedKeySpec(der))
        } catch (e: Exception) {
            null
        }

        val signable = buildSignable(proof)
        val digest = Canonical.sha256Hex(Canonical.wfCanonical(signable.fields))

        var verified = false
        if (verifyingKey != null && proof.has("signature")) {
            val algorithm = if (proof.has("algorithm")) proof.get("algorithm").asText() else null
            if (algorithm == null || algorithm == "Ed25519") {
                try {
                    val sigBytes = Base64.getDecoder().decode(proof.get("signature").asText())
                    val digestBytes = hexToBytes(digest)
                    val sig = Signature.getInstance("Ed25519")
                    sig.initVerify(verifyingKey)
                    sig.update(digestBytes)
                    verified = sig.verify(sigBytes)
                } catch (e: Exception) {
                    verified = false
                }
            }
        }

        val taskIdOut = if (proof.has("task_id")) proof.get("task_id").asText() else null

        return VerifyResult(
            verified = verified,
            taskId = taskIdOut,
            keyId = keyId,
            algorithm = "Ed25519",
            fieldsSigned = signable.fieldCount,
            trustless = true,
            message = if (verified)
                "Signature mathematically verified. This proof was signed by ForceDream and has not been altered."
            else
                "Signature verification FAILED. The proof was altered or not signed by ForceDream.",
        )
    }

    private fun pemToDer(pem: String): ByteArray {
        val body = pem.replace(Regex("-----[^-]+-----"), "").replace(Regex("\\s"), "")
        return Base64.getDecoder().decode(body)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
