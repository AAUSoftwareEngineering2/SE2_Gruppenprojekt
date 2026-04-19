package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.app.network.ApiConfig
import at.aau.kuhhandel.app.network.NetworkClientFactory
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class OpenedSession(
    val session: WebSocketSession,
    private val extraCleanup: suspend () -> Unit = {},
) {
    suspend fun close() {
        runCatching { session.close() }
        runCatching { extraCleanup() }
    }
}

/** Talks to the server's GameWebSocketHandler — one connection, typed commands, events as Flow. */
class GameWebSocketClient(
    openSession: (suspend () -> OpenedSession)? = null,
) {
    private var current: OpenedSession? = null
    private val openSession: suspend () -> OpenedSession = openSession ?: ::defaultOpenSession

    /** Opens the WebSocket and returns every incoming envelope as Flow. Collect once. */
    suspend fun connect(): Flow<WebSocketEnvelope> {
        check(current == null) { "Already connected. Call disconnect() first." }
        val opened = openSession()
        current = opened
        // flow { }: the loop starts only when someone collects, each emit is one envelope.
        return flow {
            try {
                for (frame in opened.session.incoming) {
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
                if (current === opened) {
                    current = null
                    opened.close()
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
        val active = current ?: return
        current = null
        active.close()
    }

    private suspend fun send(envelope: WebSocketEnvelope) {
        val active = current?.session ?: error("Not connected. Call connect() first.")
        val text = WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
        active.send(Frame.Text(text))
    }

    private suspend fun defaultOpenSession(): OpenedSession {
        val client = NetworkClientFactory.create()
        return try {
            val session =
                client.webSocketSession {
                    url("${ApiConfig.WS_URL}${WebSocketRoutes.GAME}")
                }
            OpenedSession(session) { client.close() }
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }
}
