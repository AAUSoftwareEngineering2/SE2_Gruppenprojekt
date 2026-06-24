package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

@Serializable
data class GameUpdatedNotification(
    val gameId: String,
)

/**
 * Receives "game updated" notifications from peer pods, reloads the state from the database
 * and republishes it as a local [GameStateChangedEvent] so the WebSocket listener can push
 * player-specific views to the sessions on this pod. Only the gameId crosses the wire.
 *
 * Protected by the shared cluster secret; without one configured every request is rejected.
 */
@RestController
class ClusterSyncController(
    private val properties: ClusterProperties,
    private val notifier: ClusterUpdateNotifier,
    private val persistenceService: GamePersistenceService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(ClusterSyncController::class.java)

    @PostMapping("/internal/cluster/game-updated")
    fun gameUpdated(
        // required = false: fehlt der Header, wirft Spring NICHT automatisch 400 - er kommt als
        // null rein und wir antworten selbst kontrolliert mit 403 (siehe isAuthorized()).
        @RequestHeader(ClusterUpdateNotifier.SECRET_HEADER, required = false) secret: String?,
        @RequestHeader(ClusterUpdateNotifier.ORIGIN_HEADER, required = false) origin: String?,
        @RequestBody notification: GameUpdatedNotification,
    ): ResponseEntity<Unit> {
        // 403 nur wenn das Secret fehlt/falsch ist. In allen anderen Fällen 204 No Content
        // (Erfolg, eigenes Echo, oder unbekanntes Spiel).
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        // Drop the echo of our own notification.
        if (origin == notifier.instanceId) {
            return ResponseEntity.noContent().build()
        }

        val state = persistenceService.loadGameState(notification.gameId)
        if (state == null) {
            logger.debug("Peer update for unknown game {} ignored", notification.gameId)
            return ResponseEntity.noContent().build()
        }

        // Internes Spring-Event (NICHT die HTTP-Antwort - die ist nur 204). Es wird vom
        // @EventListener im GameWebSocketHandler aufgefangen, der den frischen Stand dann an die
        // WebSocket-Clients dieses Pods pusht.
        eventPublisher.publishEvent(GameStateChangedEvent(notification.gameId, state))
        return ResponseEntity.noContent().build()
    }

    private fun isAuthorized(secret: String?): Boolean {
        if (!properties.clusterEnabled || secret.isNullOrBlank()) return false
        // Vergleich in konstanter Zeit: ein normales == bricht beim ersten falschen Zeichen ab -
        // dann könnte man das Secret über die Antwortzeit Zeichen für Zeichen erraten (Timing-Angriff).
        return MessageDigest.isEqual(secret.toByteArray(), properties.secret.toByteArray())
    }
}
