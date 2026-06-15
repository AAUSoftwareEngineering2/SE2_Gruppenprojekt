package at.aau.kuhhandel.server.cluster

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.InetAddress
import java.time.Duration
import java.util.UUID

@Configuration
@EnableConfigurationProperties(ClusterProperties::class)
class ClusterConfig

/**
 * Tells the other pods that a game changed so they can push fresh snapshots to their own
 * sockets. WebSocket connections are pod-local, without this a player on pod A would never
 * hear about actions handled by pod B.
 *
 * Best-effort: a lost notification only delays the UI until the next update, the state itself
 * is in the database.
 */
@Component
class ClusterUpdateNotifier(
    private val properties: ClusterProperties,
) {
    private val logger = LoggerFactory.getLogger(ClusterUpdateNotifier::class.java)
    private val notifyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Short timeouts so a hung peer cannot back up the notify pipeline.
    private val restClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(2))
                    setReadTimeout(Duration.ofSeconds(2))
                },
            ).build()

    // Sent with every notification so a pod can drop the echo of its own broadcast
    // (the headless service also resolves to the own pod IP).
    val instanceId: String = UUID.randomUUID().toString()

    fun gameUpdated(gameId: String) {
        if (!properties.clusterEnabled) return

        notifyScope.launch {
            // Peer resolution can hit DNS (headless service), so keep it off the caller thread.
            val targets = resolvePeerUrls()
            targets
                .map { baseUrl ->
                    async {
                        notifyPeer(baseUrl, gameId)
                    }
                }.awaitAll()
        }
    }

    private fun notifyPeer(
        baseUrl: String,
        gameId: String,
    ) {
        runCatching {
            restClient
                .post()
                .uri("$baseUrl/internal/cluster/game-updated")
                .header(SECRET_HEADER, properties.secret)
                .header(ORIGIN_HEADER, instanceId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(GameUpdatedNotification(gameId))
                .retrieve()
                .toBodilessEntity()
        }.onFailure { error ->
            logger.warn("Peer notify failed for game $gameId -> $baseUrl: ${error.message}")
        }
    }

    private fun resolvePeerUrls(): List<String> {
        if (properties.effectivePeers.isNotEmpty()) return properties.effectivePeers
        if (properties.peerService.isBlank()) return emptyList()
        return runCatching {
            InetAddress
                .getAllByName(properties.peerService)
                .map { address ->
                    // Bracket IPv6 literals so the URL stays valid, e.g. http://[fd00::1]:8080.
                    val host = address.hostAddress.let { if (':' in it) "[$it]" else it }
                    "http://$host:${properties.peerPort}"
                }
        }.onFailure { error ->
            logger.warn(
                "Peer DNS resolution failed for '${properties.peerService}': ${error.message}",
            )
        }.getOrDefault(emptyList())
    }

    companion object {
        const val SECRET_HEADER = "X-Cluster-Secret"
        const val ORIGIN_HEADER = "X-Origin-Instance"
    }
}
