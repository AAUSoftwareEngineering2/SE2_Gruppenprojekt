package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.app.network.ApiConfig
import at.aau.kuhhandel.app.network.NetworkClientFactory
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.JoinGamePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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
    private val clientFactory: () -> HttpClient = NetworkClientFactory::create,
    private val webSocketUrl: String = "${ApiConfig.WS_URL}${WebSocketRoutes.GAME}",
    openSession: (suspend () -> OpenedSession)? = null,
) {
    private var current: OpenedSession? = null
    private var pendingConnection: CompletableDeferred<Unit>? = null
    private var connectionFailure: Throwable? = null
    private val openSession: suspend () -> OpenedSession = openSession ?: ::defaultOpenSession

    /** Returns the connection Flow. The WebSocket opens when collection starts. */
    fun connect(): Flow<WebSocketEnvelope> {
        ensureDisconnected()
        val ready = preparePendingConnection()
        return flow {
            val opened = openSessionOrThrow(ready)
            markConnected(opened, ready)
            try {
                emitIncomingEnvelopes(opened)
            } finally {
                cleanupConnection(opened, ready)
            }
        }
    }

    private fun ensureDisconnected() {
        check(current == null && pendingConnection == null) {
            "Already connected. Call disconnect() first."
        }
    }

    private fun preparePendingConnection(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { ready ->
            connectionFailure = null
            pendingConnection = ready
        }

    private suspend fun openSessionOrThrow(ready: CompletableDeferred<Unit>): OpenedSession =
        try {
            openSession()
        } catch (e: Exception) {
            connectionFailure = e
            clearPendingConnection(ready)
            ready.completeExceptionally(e)
            throw e
        }

    private fun markConnected(
        opened: OpenedSession,
        ready: CompletableDeferred<Unit>,
    ) {
        current = opened
        connectionFailure = null
        ready.complete(Unit)
    }

    private suspend fun FlowCollector<WebSocketEnvelope>.emitIncomingEnvelopes(
        opened: OpenedSession,
    ) {
        val closeDetails = consumeIncomingFrames(opened)
        if (closeDetails != null && current === opened) {
            error(closeDetails)
        }
    }

    private suspend fun FlowCollector<WebSocketEnvelope>.consumeIncomingFrames(
        opened: OpenedSession,
    ): String? {
        for (frame in opened.session.incoming) {
            closeDetails(frame)?.let { return it }
            decodeEnvelope(frame)?.let { emit(it) }
        }
        return null
    }

    private suspend fun closeDetails(frame: Frame): String? =
        (frame as? Frame.Close)?.let { closeFrame ->
            formatCloseDetails(closeFrame.readReason())
        }

    private fun formatCloseDetails(reason: CloseReason?): String =
        if (reason != null) {
            "WebSocket closed (${reason.code}): ${
                reason.message.ifBlank { "Kein Grund angegeben" }
            }"
        } else {
            "WebSocket closed without a close reason"
        }

    private suspend fun decodeEnvelope(frame: Frame): WebSocketEnvelope? {
        val textFrame = frame as? Frame.Text ?: return null
        return runCatching {
            WebSocketJson.json.decodeFromString(
                WebSocketEnvelope.serializer(),
                textFrame.readText(),
            )
        }.getOrNull()
    }

    private suspend fun cleanupConnection(
        opened: OpenedSession,
        ready: CompletableDeferred<Unit>,
    ) {
        if (current === opened) {
            current = null
            opened.close()
        }
        clearPendingConnection(ready)
    }

    private fun clearPendingConnection(ready: CompletableDeferred<Unit>) {
        if (pendingConnection === ready) {
            pendingConnection = null
        }
    }

    suspend fun awaitConnected() {
        pendingConnection?.await()
            ?: connectionFailure?.let { throw it }
            ?: check(current != null) { "Not connected. Call connect() first." }
    }

    /** Sends CREATE_GAME. Returns the requestId so the caller can match the server reply well. */
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

    suspend fun joinGame(
        gameId: String,
        playerName: String? = null,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                JoinGamePayload.serializer(),
                JoinGamePayload(gameId, playerName),
            )
        send(WebSocketEnvelope(WebSocketType.JOIN_GAME, requestId, payload))
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
        pendingConnection = null
        connectionFailure = null
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
        val client = clientFactory()
        return try {
            val session =
                client.webSocketSession {
                    url(webSocketUrl)
                }
            OpenedSession(session) { client.close() }
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }
}
