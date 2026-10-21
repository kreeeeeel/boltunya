package ru.aigram.ai.research

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetSocketAddress

class TestSearchMcpServer : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    var limited = false
    var error = false
    var calls = 0
    var query = ""
    var key = ""
    var tool = ""
    val origin: String get() = "http://127.0.0.1:${server.address.port}"

    fun start(): TestSearchMcpServer {
        server.createContext("/mcp") { exchange ->
            if (exchange.requestMethod != "POST") {
                exchange.sendResponseHeaders(405, -1)
                exchange.close()
                return@createContext
            }
            val request = Json.parseToJsonElement(String(exchange.requestBody.readAllBytes())).jsonObject
            val id = request["id"]
            if (id == null) {
                exchange.sendResponseHeaders(202, -1)
                exchange.close()
                return@createContext
            }
            val result = if (request["method"]?.jsonPrimitive?.content == "initialize") {
                buildJsonObject {
                    put("protocolVersion", "2025-11-25")
                    put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
                    put(
                        "serverInfo",
                        buildJsonObject {
                            put("name", "fake-search")
                            put("version", "1.0")
                        },
                    )
                }
            } else {
                calls++
                key = exchange.requestHeaders.getFirst("x-api-key").orEmpty()
                tool = request["params"]?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()
                query =
                    request["params"]?.jsonObject?.get(
                        "arguments",
                    )?.jsonObject?.get("query")?.jsonPrimitive?.content.orEmpty()
                buildJsonObject {
                    put("isError", error)
                    if (limited) put("_meta", buildJsonObject { put("ai.exa/rateLimited", true) })
                    put(
                        "content",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put(
                                        "text",
                                        if (limited || error) {
                                            "Create a key at https://invalid.example/upgrade"
                                        } else {
                                            """
                                Title: Kotlin releases
                                URL: https://kotlinlang.org/docs/releases.html
                                Published: 2026-10-10
                                Author: JetBrains
                                Highlights:
                                Kotlin stable release details.

                                ---

                                Title: Kotlin project
                                URL: https://github.com/JetBrains/kotlin
                                Published: N/A
                                Author: N/A
                                Text: Kotlin official project.
                                            """.trimIndent()
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }
            val response = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", result)
            }
            val bytes = ("event: message\ndata: $response\n\n").toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return this
    }

    override fun close() {
        server.stop(0)
    }
}
