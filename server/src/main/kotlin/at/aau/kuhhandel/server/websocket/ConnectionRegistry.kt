package at.aau.kuhhandel.server.websocket

import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Infrastructure registry managing transient network routing for active connections.
 */
@Component
class ConnectionRegistry {
    private val gameBySessionId = ConcurrentHashMap<String, String>()
    private val playerBySessionId = ConcurrentHashMap<String, String>()
    private val sessionsByGameId = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessionBySessionId = ConcurrentHashMap<String, WebSocketSession>()

    /**
     * Registers a new raw network socket under its ID.
     */
    fun bindSession(session: WebSocketSession) {
        sessionBySessionId[session.id] = session
    }

    /**
     * Binds a registered connection instance to a running game instance.
     */
    fun bindGame(
        sessionId: String,
        gameId: String,
    ) {
        val existing = gameBySessionId.putIfAbsent(sessionId, gameId)
        if (existing != null) return

        sessionsByGameId.computeIfAbsent(gameId) { ConcurrentHashMap.newKeySet() }.add(sessionId)
    }

    /**
     * Binds a registered connection instance to a player identity.
     */
    fun bindPlayer(
        sessionId: String,
        playerId: String,
    ) {
        playerBySessionId.putIfAbsent(sessionId, playerId)
    }

    fun sessionFor(sessionId: String): WebSocketSession? = sessionBySessionId[sessionId]

    fun gameIdFor(sessionId: String): String? = gameBySessionId[sessionId]

    fun playerIdFor(sessionId: String): String? = playerBySessionId[sessionId]

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
        val gameId = gameBySessionId.remove(sessionId)
        playerBySessionId.remove(sessionId)
        sessionBySessionId.remove(sessionId)

        if (gameId != null) {
            sessionsByGameId[gameId]?.remove(sessionId)
            if (sessionsByGameId[gameId].isNullOrEmpty()) {
                sessionsByGameId.remove(gameId)
            }
        }
    }
}
