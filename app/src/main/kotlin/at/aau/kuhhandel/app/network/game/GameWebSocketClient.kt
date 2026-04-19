package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.app.network.ApiConfig
import at.aau.kuhhandel.app.network.NetworkClientFactory
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/** Talks to the server's GameWebSocketHandler — one connection, typed commands, events as Flow. */
class GameWebSocketClient(
    openSession: (suspend () -> WebSocketSession)? = null,
) {
    private var session: WebSocketSession? = null
    private var ownedHttpClient: HttpClient? = null
    private val openSession: suspend () -> WebSocketSession = openSession ?: ::defaultOpenSession

    /** Opens the WebSocket and returns every incoming envelope as Flow. Collect once. */
    suspend fun connect(): Flow<WebSocketEnvelope> {
        check(session == null) { "Already connected. Call disconnect() first." }
        val newSession = openSession()
        session = newSession
        // flow { } the loop starts only when someone collects, each emit is one envelope.
        return flow {
            try {
                for (frame in newSession.incoming) {
                    if (frame is Frame.Text) {
                        runCatching {
                            WebSocketJson.json.decodeFromString(
                                WebSocketEnvelope.serializer(),
                                frame.readText(),
                            )
                        }.getOrNull()?.let { emit(it) }
                    }
                }
            } finally {
                if (session === newSession) {
                    session = null
                    closeConnection(newSession)
                }
            }
        }
    }

    /** Sends CREATE_GAME. Returns the requestId so the caller can match the server reply. */
    suspend fun createGame(playerName: String? = null): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                CreateGamePayload.serializer(),
                CreateGamePayload(playerName),
            )
        send(WebSocketEnvelope(WebSocketType.CREATE_GAME, requestId, payload))
        return requestId
    }

    suspend fun startGame(): String {
        val requestId = UUID.randomUUID().toString()
        send(WebSocketEnvelope(WebSocketType.START_GAME, requestId))
        return requestId
    }

    suspend fun revealCard(): String {
        val requestId = UUID.randomUUID().toString()
        send(WebSocketEnvelope(WebSocketType.REVEAL_CARD, requestId))
        return requestId
    }

    suspend fun disconnect() {
        val activeSession = session ?: return
        session = null
        closeConnection(activeSession)
    }

    private suspend fun send(envelope: WebSocketEnvelope) {
        val active = session ?: error("Not connected. Call connect() first.")
        val text = WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
        active.send(Frame.Text(text))
    }

    private suspend fun closeConnection(activeSession: WebSocketSession) {
        runCatching { activeSession.close() }
        ownedHttpClient?.let { client ->
            ownedHttpClient = null
            runCatching { client.close() }
        }
    }

    private suspend fun defaultOpenSession(): WebSocketSession {
        val client = NetworkClientFactory.create()
        return runCatching {
            client.webSocketSession {
                url("${ApiConfig.WS_URL}${WebSocketRoutes.GAME}")
            }
        }.onSuccess {
            ownedHttpClient = client
        }.getOrElse { error ->
            client.close()
            throw error
        }
    }
}
