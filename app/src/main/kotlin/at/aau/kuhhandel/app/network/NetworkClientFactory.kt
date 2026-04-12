package at.aau.kuhhandel.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object NetworkClientFactory {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = true
        }

    fun create(): HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }

            install(WebSockets)
        }
}
