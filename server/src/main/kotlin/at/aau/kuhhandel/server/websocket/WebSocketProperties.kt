package at.aau.kuhhandel.server.websocket

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kuhhandel.websocket")
data class WebSocketProperties(
    // Web-Origins, die eine WS-Verbindung aufbauen dürfen. Defaults nur fürs lokale Entwickeln
    // (8080 = Server selbst, 3000 = typischer Frontend-Dev-Port); in Prod via Config überschrieben.
    val allowedOrigins: List<String> =
        listOf(
            "http://localhost:8080",
            "http://localhost:3000",
        ),
) {
    init {
        // Verbietet die Wildcard "*": sonst dürfte JEDE fremde Website eine WS-Verbindung aufbauen
        // (Cross-Site WebSocket Hijacking). none{...} = "kein Eintrag ist *"; sonst startet die App nicht. none: geht alles durch von Liste. trim entfernt leerzeichen. if * nicht starten
        require(allowedOrigins.none { it.trim() == "*" }) {
            "kuhhandel.websocket.allowed-origins must not contain wildcard '*'"
        }
    }
}
