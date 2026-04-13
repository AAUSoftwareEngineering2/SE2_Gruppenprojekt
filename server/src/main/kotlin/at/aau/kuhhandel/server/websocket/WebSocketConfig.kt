package at.aau.kuhhandel.server.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val gameWebSocketHandler: GameWebSocketHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(gameWebSocketHandler, "/websocket/game")
            // Allow all origins for local development; should be restricted when more game logic is implemented
            .setAllowedOrigins("*")
    }
}
