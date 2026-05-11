package at.aau.kuhhandel.server.websocket

import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Component
class ConnectionRegistry {
    private val gameBySessionId = ConcurrentHashMap<String, String>()
    private val sessionsById = ConcurrentHashMap<String, WebSocketSession>()

    fun bind(
        session: WebSocketSession,
        gameId: String,
    ) {
        gameBySessionId[session.id] = gameId
        sessionsById[session.id] = session
    }

    fun gameIdFor(sessionId: String): String? = gameBySessionId[sessionId]

    fun getSessionsForGame(gameId: String): List<WebSocketSession> =
        gameBySessionId
            .filterValues {
                it == gameId
            }.keys
            .mapNotNull { sessionsById[it] }

    fun unbind(sessionId: String) {
        gameBySessionId.remove(sessionId)
        sessionsById.remove(sessionId)
    }
}
