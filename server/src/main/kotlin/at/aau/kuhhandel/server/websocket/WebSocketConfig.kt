package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

/**
 * Registers the game WebSocket endpoint and configures handshake rules.
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties::class)
class WebSocketConfig(
    private val gameWebSocketHandler: GameWebSocketHandler,
    private val properties: WebSocketProperties,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val allowedOrigins =
            properties.allowedOrigins
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toTypedArray()
        require(allowedOrigins.isNotEmpty()) {
            "kuhhandel.websocket.allowed-origins must contain at least one origin"
        }

        registry
            .addHandler(gameWebSocketHandler, WebSocketRoutes.GAME)
            .setAllowedOrigins(*allowedOrigins)
    }

    // Cloudflare free tier closes idle WebSockets after ~100s. We send our own pings every 25s
    // (see WebSocketHeartbeat), but the container's own idle timeout must outlive that — otherwise
    // Tomcat would close sessions before the heartbeat fires. 5 minutes leaves headroom for slow
    // clients. setAsyncSendTimeout caps how long a single send may block before being aborted.
    @Bean
    fun servletServerContainerFactoryBean(): ServletServerContainerFactoryBean =
        ServletServerContainerFactoryBean().apply {
            maxSessionIdleTimeout = 5L * 60L * 1000L
            setAsyncSendTimeout(10_000L)
        }
}
