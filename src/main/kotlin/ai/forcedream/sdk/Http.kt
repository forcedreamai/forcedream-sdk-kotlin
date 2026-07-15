package ai.forcedream.sdk

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Thin wrapper over Java's built-in HttpClient (since Java 11), used directly via Kotlin's
 * full JVM interop -- no external HTTP dependency needed, matching the real Java SDK's
 * approach exactly.
 */
object Http {
    val MAPPER: ObjectMapper = ObjectMapper()

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    data class Result(val status: Int, val json: JsonNode)

    fun get(url: String, apiKey: String? = null): Result {
        val builder = HttpRequest.newBuilder(URI.create(url)).GET()
        if (apiKey != null) builder.header("Authorization", "Bearer $apiKey")
        val res = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val json = try {
            MAPPER.readTree(res.body())
        } catch (e: Exception) {
            MAPPER.nullNode()
        }
        return Result(res.statusCode(), json)
    }

    fun post(url: String, body: Any, apiKey: String? = null): Result {
        val bodyJson = MAPPER.writeValueAsString(body)
        val builder = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
        if (apiKey != null) builder.header("Authorization", "Bearer $apiKey")
        val res = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val json = try {
            MAPPER.readTree(res.body())
        } catch (e: Exception) {
            MAPPER.nullNode()
        }
        return Result(res.statusCode(), json)
    }
}
