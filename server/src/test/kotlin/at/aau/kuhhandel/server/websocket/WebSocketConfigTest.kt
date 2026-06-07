package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import kotlin.test.assertFailsWith

class WebSocketConfigTest {
    @Test
    fun `registers only configured websocket origins`() {
        val handler = mockk<GameWebSocketHandler>()
        val registry = mockk<WebSocketHandlerRegistry>()
        val registration = mockk<WebSocketHandlerRegistration>()
        every { registry.addHandler(handler, WebSocketRoutes.GAME) } returns registration
        every { registration.setAllowedOrigins(*anyVararg()) } returns registration

        WebSocketConfig(
            gameWebSocketHandler = handler,
            properties = WebSocketProperties(allowedOrigins = listOf("https://app.example")),
        ).registerWebSocketHandlers(registry)

        verify { registration.setAllowedOrigins("https://app.example") }
        verify(exactly = 0) { registration.setAllowedOrigins("*") }
    }

    @Test
    fun `rejects wildcard websocket origins`() {
        assertFailsWith<IllegalArgumentException> {
            WebSocketProperties(allowedOrigins = listOf("*"))
        }
    }
}
