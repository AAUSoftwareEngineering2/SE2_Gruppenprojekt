package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * Registers the game WebSocket endpoint and configures handshake rules.
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val gameWebSocketHandler: GameWebSocketHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(gameWebSocketHandler, WebSocketRoutes.GAME)
            // Temporary development setting; should be restricted when origins are finalized
            .setAllowedOrigins("*")
    }
}
