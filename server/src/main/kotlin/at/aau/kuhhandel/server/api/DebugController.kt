package at.aau.kuhhandel.server.api

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DebugController(
    private val persistenceService: GamePersistenceService? = null,
) {
    @GetMapping("/debug/persistence")
    fun checkPersistence(): Map<String, Any> {
        if (persistenceService == null) {
            return mapOf(
                "status" to "NO_DB",
                "message" to "persistenceService is null — no database bean configured",
            )
        }
        return try {
            persistenceService.loadGameState("00000") // harmless probe, returns null for unknown game
            mapOf("status" to "OK", "message" to "Database connection is working")
        } catch (e: Exception) {
            mapOf("status" to "ERROR", "message" to (e.message ?: e.javaClass.simpleName))
        }
    }
}
