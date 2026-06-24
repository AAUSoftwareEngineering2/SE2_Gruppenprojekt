package at.aau.kuhhandel.app.network

import at.aau.kuhhandel.shared.websocket.WebSocketJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit

// baut den Ktor-HTTP-Client (OkHttp-Engine), den die App für die WebSocket-Verbindung nutzt.
object NetworkClientFactory {
    fun create(): HttpClient =
        HttpClient(OkHttp) {
            engine {
                config {
                    // Client-Ping alle 20s (Keep-Alive) - das Gegenstück zum 25s-Server-Heartbeat.
                    pingInterval(20, TimeUnit.SECONDS)
                }
            }

            install(ContentNegotiation) {
                json(WebSocketJson.json)
            }

            install(WebSockets)
        }
}
