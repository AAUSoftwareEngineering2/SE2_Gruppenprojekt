package at.aau.kuhhandel.server.websocket

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ConnectionRegistry {
    private val gameBySessionId = ConcurrentHashMap<String, String>()
    private val sessionsByGameId = ConcurrentHashMap<String, MutableSet<String>>()

    fun bind(
        sessionId: String,
        gameId: String,
    ): Boolean {
        val existing = gameBySessionId.putIfAbsent(sessionId, gameId)
        if (existing != null) return false

        sessionsByGameId.computeIfAbsent(gameId) { mutableSetOf() }.add(sessionId)
        return true
    }

    fun gameIdFor(sessionId: String): String? = gameBySessionId[sessionId]

    fun sessionIdsFor(gameId: String): Set<String> = sessionsByGameId[gameId]?.toSet().orEmpty()

    fun unbind(sessionId: String) {
        val gameId = gameBySessionId.remove(sessionId) ?: return
        sessionsByGameId[gameId]?.remove(sessionId)
        if (sessionsByGameId[gameId].isNullOrEmpty()) {
            sessionsByGameId.remove(gameId)
        }
    }
}
