package at.aau.kuhhandel.server.api

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
// Endpoint wird NUR erzeugt, wenn kuhhandel.debug.persistence.enabled=true gesetzt ist.
// Kein matchIfMissing -> standardmäßig AUS (kein offener Debug-Endpoint in Produktion).
@ConditionalOnProperty(
    prefix = "kuhhandel.debug.persistence",
    name = ["enabled"],
    havingValue = "true",
)
class DebugController(
    private val persistenceService: GamePersistenceService? = null,
) {
    private val logger = LoggerFactory.getLogger(DebugController::class.java)

    @GetMapping("/debug/persistence")
    fun checkPersistence(): Map<String, Any> {
        if (persistenceService == null) {
            return mapOf(
                "status" to "NO_DB",
                "message" to "persistenceService is null — no database bean configured",
            )
        }
        return try {
            persistenceService.loadGameState("00000") // harmless probe; returns null if not found
            mapOf("status" to "OK", "message" to "Database connection is working")
        } catch (e: Exception) {
            // Fehler loggen (nur Typ in warn, Details in debug) und generische ERROR-Antwort -
            // keine internen Details an den Client (kein Info-Leak).
            logger.warn("Debug persistence check failed: {}", e.javaClass.simpleName)
            logger.debug("Debug persistence check failed", e)
            mapOf("status" to "ERROR", "message" to "Database connection check failed")
        }
    }
}
