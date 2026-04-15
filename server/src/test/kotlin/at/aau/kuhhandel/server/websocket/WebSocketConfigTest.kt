package at.aau.kuhhandel.server.websocket

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.mockito.Mockito.`when` as whenever

class WebSocketConfigTest {
    private lateinit var handler: GameWebSocketHandler
    private lateinit var config: WebSocketConfig

    @BeforeEach
    fun setUp() {
        handler = GameWebSocketHandler()
        config = WebSocketConfig(handler)
    }

    @Test
    fun `registerWebSocketHandlers registers handler and allowed origins`() {
        val registry = mock(WebSocketHandlerRegistry::class.java)
        val registration = mock(WebSocketHandlerRegistration::class.java)

        whenever(registry.addHandler(handler, "/websocket/game")).thenReturn(registration)
        whenever(registration.setAllowedOrigins("*")).thenReturn(registration)

        config.registerWebSocketHandlers(registry)

        verify(registry).addHandler(handler, "/websocket/game")
        verify(registration).setAllowedOrigins("*")
    }
}
