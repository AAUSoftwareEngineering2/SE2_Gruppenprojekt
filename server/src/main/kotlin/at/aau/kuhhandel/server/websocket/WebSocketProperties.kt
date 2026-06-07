package at.aau.kuhhandel.server.websocket

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kuhhandel.websocket")
data class WebSocketProperties(
    val allowedOrigins: List<String> =
        listOf(
            "http://localhost:8080",
            "http://localhost:3000",
        ),
) {
    init {
        require(allowedOrigins.none { it.trim() == "*" }) {
            "kuhhandel.websocket.allowed-origins must not contain wildcard '*'"
        }
    }
}
