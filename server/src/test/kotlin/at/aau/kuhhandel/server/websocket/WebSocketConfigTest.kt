package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import kotlin.test.assertEquals
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
    fun `uses localhost websocket origins by default`() {
        assertEquals(
            listOf("http://localhost:8080", "http://localhost:3000"),
            WebSocketProperties().allowedOrigins,
        )
    }

    @Test
    fun `rejects wildcard websocket origins`() {
        assertFailsWith<IllegalArgumentException> {
            WebSocketProperties(allowedOrigins = listOf("*"))
        }
    }

    @Test
    fun `rejects empty websocket origin configuration`() {
        val handler = mockk<GameWebSocketHandler>()
        val registry = mockk<WebSocketHandlerRegistry>(relaxed = true)

        assertFailsWith<IllegalArgumentException> {
            WebSocketConfig(
                gameWebSocketHandler = handler,
                properties = WebSocketProperties(allowedOrigins = listOf(" ", "")),
            ).registerWebSocketHandlers(registry)
        }
    }

    @Test
    fun `configures websocket container idle timeout`() {
        val config =
            WebSocketConfig(
                gameWebSocketHandler = mockk(relaxed = true),
                properties = WebSocketProperties(allowedOrigins = listOf("https://app.example")),
            )

        val factoryBean = config.servletServerContainerFactoryBean()

        assertEquals(5L * 60L * 1000L, factoryBean.maxSessionIdleTimeout)
    }
}
