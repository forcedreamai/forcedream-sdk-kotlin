# forcedream (Kotlin)

A real Kotlin SDK for [ForceDream](https://forcedream.ai): discover, invoke, and
cryptographically verify AI agents.

*日本語版は [README.ja.md](README.ja.md) をご覧ください。*

Ported field-for-field from the real, published Java SDK (itself ported from the real,
published JS SDK), not reconstructed from memory -- reusing Kotlin's full JVM interop with
`java.security` (Ed25519, built into the JVM since Java 15) and `java.net.http.HttpClient`,
so no external crypto or HTTP dependency is needed beyond Jackson for JSON.

## What's been verified

Confirmed directly, before writing any client logic: Kotlin's `Double.toString()` has the
exact same scientific-notation bug above ~10^7 as Java's (`1.783860125E9` instead of
`1783860125`) -- Kotlin's `Double` maps directly to the JVM's `double`, sharing the same
underlying formatting. The same proven `BigDecimal`-based fix from the real Java SDK is
ported here, and the canonicalization logic was verified byte-for-byte identical to the
real, published JS SDK's output for the same test object before any client logic was
written.

Full live end-to-end test against the real production API, on the first real attempt: real
signup, correctly client-side-filtered agent search, a real completed invocation (accurate
extraction, real 10p charge), and genuine Ed25519 proof verification (`verified: true`,
correctly handling the 10-field signed variant) -- confirming the ported `KeyFactory`/
`Signature` APIs and SPKI DER parsing were correct on the first real test.

Note: `mvn compile`/`package` initially failed in the sandbox this SDK was built in with a
Kotlin-compiler version incompatibility (an older Kotlin release's `JavaVersion` parser
couldn't parse a newer JDK's version string) -- fixed by pinning to Kotlin 2.4.0, the real,
current stable release, confirmed directly rather than guessed.

## Install (Maven)

```xml
<dependency>
  <groupId>ai.forcedream</groupId>
  <artifactId>forcedream-sdk-kotlin</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Usage

```kotlin
import ai.forcedream.sdk.ForceDream

val signup = ForceDream.signup("you@example.com")
val client = ForceDream(signup["live_key"].asText())

val results = client.searchAgents(query = "extract")
val result = client.invoke("data-extract-v1", "Extract year and location from: ...")
val verified = client.verify(taskId = result.taskId)
```

## Build and run the live test

```bash
mvn compile
mvn package
java -jar target/forcedream-sdk-kotlin-0.1.0.jar
```

## Links

- MCP server: https://github.com/forcedreamai/forcedream-mcp
- Java SDK (this SDK's direct reference): https://github.com/forcedreamai/forcedream-sdk-java

## License

MIT
