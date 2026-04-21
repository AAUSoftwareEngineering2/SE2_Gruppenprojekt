package at.aau.kuhhandel.app.network

import at.aau.kuhhandel.shared.websocket.WebSocketJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

object NetworkClientFactory {
    fun create(): HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(WebSocketJson.json)
            }

            install(WebSockets)
        }
}
