package at.aau.kuhhandel.server.websocket

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ConnectionRegistry {
    private val gameBySessionId = ConcurrentHashMap<String, String>()

    fun bind(
        sessionId: String,
        gameId: String,
    ) {
        gameBySessionId[sessionId] = gameId
    }

    fun gameIdFor(sessionId: String): String? = gameBySessionId[sessionId]

    fun unbind(sessionId: String) {
        gameBySessionId.remove(sessionId)
    }
}
