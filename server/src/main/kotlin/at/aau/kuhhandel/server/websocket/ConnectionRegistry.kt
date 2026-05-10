package at.aau.kuhhandel.server.websocket

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ConnectionRegistry {
    private val gameBySessionId = ConcurrentHashMap<String, String>()
    private val playerBySessionId = ConcurrentHashMap<String, String>()
    private val sessionsByGameId = ConcurrentHashMap<String, MutableSet<String>>()

    fun bindGame(
        sessionId: String,
        gameId: String,
    ) {
        val existing = gameBySessionId.putIfAbsent(sessionId, gameId)
        if (existing != null) return

        sessionsByGameId.computeIfAbsent(gameId) { ConcurrentHashMap.newKeySet() }.add(sessionId)
    }

    fun bindPlayer(
        sessionId: String,
        playerId: String,
    ) {
        playerBySessionId.putIfAbsent(sessionId, playerId)
    }

    fun gameIdFor(sessionId: String): String? = gameBySessionId[sessionId]

    fun playerIdFor(sessionId: String): String? = playerBySessionId[sessionId]

    fun sessionIdsFor(gameId: String): Set<String> = sessionsByGameId[gameId]?.toSet().orEmpty()

    fun unbind(sessionId: String) {
        val gameId = gameBySessionId.remove(sessionId)

        if (gameId != null) {
            sessionsByGameId[gameId]?.remove(sessionId)
            if (sessionsByGameId[gameId].isNullOrEmpty()) {
                sessionsByGameId.remove(gameId)
            }
        }

        playerBySessionId.remove(sessionId)
    }
}
