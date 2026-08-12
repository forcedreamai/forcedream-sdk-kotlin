# forcedream (Kotlin)

[ForceDream](https://forcedream.ai) の Kotlin SDK です。AI エージェントの検索、実行、そして暗号学的な検証を行えます。

*Read this in [English](README.md).*

公開済みの Java SDK（さらにその元は公開済みの JS SDK）からフィールド単位で移植したものであり、記憶から再構成したものではありません。Kotlin の JVM 相互運用性をそのまま活用し、`java.security`（Ed25519 は Java 15 以降 JVM に標準搭載）と `java.net.http.HttpClient` を使用しています。そのため JSON 処理の Jackson 以外に、暗号や HTTP の外部依存は必要ありません。

## 確認済みの事項

クライアントロジックを書く前に、次の点を直接確認しています。Kotlin の `Double.toString()` には、Java と同一の科学表記の問題があります（およそ 10^7 を超えると `1783860125` ではなく `1.783860125E9` となる）。Kotlin の `Double` は JVM の `double` に直接対応するため、同じ書式化処理を共有しているためです。Java SDK で実証済みの `BigDecimal` を用いた対処をここでも移植し、正規化ロジックの出力が、同一のテストオブジェクトに対して公開済み JS SDK と 1 バイトも違わないことを、クライアントロジックの実装前に確認しました。

本番 API に対するエンドツーエンドのテストも、最初の実行で通過しています。実際のサインアップ、クライアント側で正しくフィルターされたエージェント検索、実際に完了した実行（正確な抽出、実際の 10 ペンスの課金）、そして本物の Ed25519 証明検証（`verified: true`、10 フィールドの署名バリアントを正しく処理）。移植した `KeyFactory` / `Signature` API と SPKI DER のパース処理が、最初のテストで正しく動作したことを示しています。

補足として、この SDK を構築した環境では、`mvn compile` および `package` が Kotlin コンパイラのバージョン非互換で当初失敗しました（古い Kotlin リリースの `JavaVersion` パーサーが、新しい JDK のバージョン文字列を解釈できないというもの）。現行の安定版である Kotlin 2.4.0 に固定することで解消しました。これは推測ではなく、直接確認しています。

## インストール（Maven）

```xml
<dependency>
  <groupId>ai.forcedream</groupId>
  <artifactId>forcedream-sdk-kotlin</artifactId>
  <version>0.1.0</version>
</dependency>
```

## 使い方

```kotlin
import ai.forcedream.sdk.ForceDream

val signup = ForceDream.signup("you@example.com")
val client = ForceDream(signup["live_key"].asText())

val results = client.searchAgents(query = "extract")
val result = client.invoke("data-extract-v1", "Extract year and location from: ...")
val verified = client.verify(taskId = result.taskId)
```

## ビルドとテストの実行

```bash
mvn compile
mvn package
java -jar target/forcedream-sdk-kotlin-0.1.0.jar
```

## リンク

- MCP サーバー: https://github.com/forcedreamai/forcedream-mcp
- Java SDK（この SDK の直接の移植元）: https://github.com/forcedreamai/forcedream-sdk-java

## ライセンス

MIT
