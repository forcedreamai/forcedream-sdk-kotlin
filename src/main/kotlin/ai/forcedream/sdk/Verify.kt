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

    /**
     * Exact replica of the server's verifyMerkleInclusion. Each sibling carries its own
     * position, so ordering is never derived from leaf_index. Hashing is over concatenated
     * HEX STRINGS, not raw bytes -- matching the server exactly. An empty sibling array
     * means the root is the leaf digest unchanged (the batch_size == 1 case, which is every
     * real proof the platform has emitted to date).
     */
    fun verifyMerkleInclusion(leafHash: String, siblings: JsonNode?, expectedRoot: String): Boolean {
        if (siblings == null || !siblings.isArray) return false
        return try {
            var current = leafHash
            for (step in siblings) {
                if (!step.has("hash") || !step.get("hash").isTextual) return false
                val siblingHash = step.get("hash").asText()
                val position = if (step.has("position")) step.get("position").asText() else null
                current = if (position == "right") {
                    Canonical.sha256Hex(current + siblingHash)
                } else {
                    Canonical.sha256Hex(siblingHash + current)
                }
            }
            current == expectedRoot
        } catch (e: Exception) {
            // Contract: verification failure returns false, it never raises.
            false
        }
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

        val proofAlgorithm =
            if (proof.has("algorithm") && !proof.get("algorithm").isNull)
                proof.get("algorithm").asText() else null

        var verified = false
        if (verifyingKey != null && proof.has("signature")) {
            try {
                val sigBytes = Base64.getDecoder().decode(proof.get("signature").asText())
                val sig = Signature.getInstance("Ed25519")
                sig.initVerify(verifyingKey)

                if (proofAlgorithm == "Ed25519-batched") {
                    // A batched proof is only as strong as this real double-check: the
                    // digest must genuinely be a leaf of the claimed root, verified BEFORE
                    // the signature is trusted. The signature is over the ROOT, not the
                    // digest.
                    val root =
                        if (proof.has("merkle_root") && proof.get("merkle_root").isTextual)
                            proof.get("merkle_root").asText() else null
                    val siblings = proof.get("inclusion_proof")?.get("siblings")

                    if (!root.isNullOrEmpty() && verifyMerkleInclusion(digest, siblings, root)) {
                        sig.update(hexToBytes(root))
                        verified = sig.verify(sigBytes)
                    }
                } else if (proofAlgorithm == null || proofAlgorithm == "Ed25519") {
                    sig.update(hexToBytes(digest))
                    verified = sig.verify(sigBytes)
                }
            } catch (e: Exception) {
                verified = false
            }
        }

        val taskIdOut = if (proof.has("task_id")) proof.get("task_id").asText() else null

        return VerifyResult(
            verified = verified,
            taskId = taskIdOut,
            keyId = keyId,
            algorithm = proofAlgorithm ?: "Ed25519",
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
