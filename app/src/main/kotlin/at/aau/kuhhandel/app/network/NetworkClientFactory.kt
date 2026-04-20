package at.aau.kuhhandel.app.network

import at.aau.kuhhandel.shared.websocket.WebSocketJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit

object NetworkClientFactory {
    fun create(): HttpClient =
        HttpClient(OkHttp) {
            engine {
                config {
                    pingInterval(20, TimeUnit.SECONDS)
                }
            }

            install(ContentNegotiation) {
                json(WebSocketJson.json)
            }

            install(WebSockets)
        }
}
