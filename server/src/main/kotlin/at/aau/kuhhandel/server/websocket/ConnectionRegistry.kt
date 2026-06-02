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
    private val sessionsByGameId = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessionBySessionId = ConcurrentHashMap<String, WebSocketSession>()

    /**
     * Registers a new raw network socket under its ID.
     */
    fun bindSession(session: WebSocketSession) {
        sessionBySessionId[session.id] = session
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
        sessionsByGameId.computeIfAbsent(gameId) { ConcurrentHashMap.newKeySet() }.add(sessionId)
    }

    fun sessionFor(sessionId: String): WebSocketSession? = sessionBySessionId[sessionId]

    /**
     * Retrieves the UserSession associated with a WebSocket session ID.
     */
    fun playerSessionFor(sessionId: String): PlayerSession? = playerSessions[sessionId]

    fun sessionIdsFor(gameId: String): Set<String> = sessionsByGameId[gameId]?.toSet().orEmpty()

    fun sessionsFor(gameId: String): Set<WebSocketSession> =
        sessionIdsFor(gameId).mapNotNull { sessionBySessionId[it] }.toSet()

    /**
     * Snapshot of every currently bound session. Used by WebSocketHeartbeat to ping every live
     * connection without iterating per-game.
     */
    fun allSessions(): Collection<WebSocketSession> = sessionBySessionId.values.toList()

    /**
     * Unbinds a connection instance, removing all data associated with it.
     */
    fun unbind(sessionId: String) {
        val boundSession = playerSessions.remove(sessionId)
        sessionBySessionId.remove(sessionId)

        if (boundSession != null) {
            sessionsByGameId[boundSession.gameId]?.remove(sessionId)
            if (sessionsByGameId[boundSession.gameId].isNullOrEmpty()) {
                sessionsByGameId.remove(boundSession.gameId)
            }
        }
    }
}
