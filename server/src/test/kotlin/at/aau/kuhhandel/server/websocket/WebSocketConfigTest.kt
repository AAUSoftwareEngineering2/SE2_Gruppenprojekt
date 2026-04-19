package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.mockito.Mockito.`when` as whenever

class WebSocketConfigTest {
    private lateinit var gameService: GameService
    private lateinit var connectionRegistry: ConnectionRegistry
    private lateinit var handler: GameWebSocketHandler
    private lateinit var config: WebSocketConfig

    @BeforeEach
    fun setUp() {
        gameService = mock(GameService::class.java)
        connectionRegistry = mock(ConnectionRegistry::class.java)

        handler = GameWebSocketHandler(gameService, connectionRegistry)
        config = WebSocketConfig(handler)
    }

    @Test
    fun `registerWebSocketHandlers registers handler and allowed origins`() {
        val registry = mock(WebSocketHandlerRegistry::class.java)
        val registration = mock(WebSocketHandlerRegistration::class.java)

        whenever(registry.addHandler(handler, WebSocketRoutes.GAME)).thenReturn(registration)
        whenever(registration.setAllowedOrigins("*")).thenReturn(registration)

        config.registerWebSocketHandlers(registry)

        verify(registry).addHandler(handler, WebSocketRoutes.GAME)
        verify(registration).setAllowedOrigins("*")
    }
}
