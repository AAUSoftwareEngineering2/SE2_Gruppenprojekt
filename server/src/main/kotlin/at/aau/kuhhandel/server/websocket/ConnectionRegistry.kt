package at.aau.kuhhandel.server.websocket

import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Infrastructure registry managing transient network routing for active connections.
 */
@Component
class ConnectionRegistry {
    private val playerSessions = ConcurrentHashMap<String, PlayerSession>()
    private val connectionIdsByGameId = ConcurrentHashMap<String, MutableSet<String>>()
    private val connections = ConcurrentHashMap<String, WebSocketSession>()

    /**
     * Registers a new raw network socket under its ID.
     */
    fun bindConnection(session: WebSocketSession) {
        connections[session.id] = session
    }

    /**
     * Binds a game ID, player ID, and reconnection token to a WebSocket session ID.
     */
    fun bindPlayerSession(
        sessionId: String,
        gameId: String,
        playerId: String,
    ) {
        playerSessions[sessionId] = PlayerSession(gameId, playerId)
        connectionIdsByGameId
            .computeIfAbsent(gameId) { ConcurrentHashMap.newKeySet() }
            .add(sessionId)
    }

    fun connectionFor(sessionId: String): WebSocketSession? = connections[sessionId]

    /**
     * Retrieves the UserSession associated with a WebSocket session ID.
     */
    fun playerSessionFor(sessionId: String): PlayerSession? = playerSessions[sessionId]

    fun connectionIdsFor(gameId: String): Set<String> =
        connectionIdsByGameId[gameId]?.toSet().orEmpty()

    fun connectionsFor(gameId: String): Set<WebSocketSession> =
        connectionIdsFor(gameId).mapNotNull { connections[it] }.toSet()

    /**
     * Snapshot of every currently bound WebSocket session. Used by WebSocketHeartbeat to ping
     * every live connection without iterating per-game.
     */
    fun allConnections(): Collection<WebSocketSession> = connections.values.toList()

    /**
     * Unbinds a connection instance, removing all data associated with it.
     */
    fun unbind(sessionId: String) {
        val boundSession = playerSessions.remove(sessionId)
        connections.remove(sessionId)

        if (boundSession != null) {
            connectionIdsByGameId[boundSession.gameId]?.remove(sessionId)
            if (connectionIdsByGameId[boundSession.gameId].isNullOrEmpty()) {
                connectionIdsByGameId.remove(boundSession.gameId)
            }
        }
    }
}
