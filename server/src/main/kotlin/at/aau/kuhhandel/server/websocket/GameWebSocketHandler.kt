package at.aau.kuhhandel.server.websocket

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

/**
 * Handles game WebSocket messages.
 */
@Component
class GameWebSocketHandler : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        session.sendMessage(TextMessage("CONNECTED"))
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        when (message.payload.trim().uppercase()) {
            "HELLO" -> session.sendMessage(TextMessage("WELCOME"))
            else -> session.sendMessage(TextMessage("UNKNOWN"))
        }
    }
}
