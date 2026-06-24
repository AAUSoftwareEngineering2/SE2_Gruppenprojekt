package at.aau.kuhhandel.server.cluster

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Multi-pod coordination settings.
 *
 * [peers] is a static URL list for local testing, [peerService] a DNS name that resolves to
 * all pod IPs (Kubernetes headless service). Without either the server runs single-pod and
 * cluster sync stays off. [secret] is required once peers are configured because the internal
 * endpoint is reachable through the public tunnel.
 */
@ConfigurationProperties(prefix = "kuhhandel.cluster")
data class ClusterProperties(
    // peers: feste Liste von Peer-URLs, die man manuell setzt (vor allem zum lokalen Testen).
    // peerService: DNS-Name des Kubernetes "Headless Service" -> löst auf ALLE Pod-IPs auf, damit
    // findet ein Pod automatisch alle anderen. Also kein einzelner Pod-Identifier.
    val peers: List<String> = emptyList(),
    val peerService: String = "",
    val peerPort: Int = 8080,
    val secret: String = "",
) {
    // An empty KUHHANDEL_CLUSTER_PEERS env var binds as one blank entry, filter those out.
    val effectivePeers: List<String> = peers.filter { it.isNotBlank() }

    init {
        // Sicherheits-Check beim Start: sobald Peers/peerService gesetzt sind, MUSS auch ein Secret
        // da sein - sonst startet die App gar nicht. Grund: der /internal-Endpoint ist über den
        // öffentlichen Cloudflare-Tunnel erreichbar, ohne Secret wäre er offen im Netz.
        require(effectivePeers.isEmpty() && peerService.isBlank() || secret.isNotBlank()) {
            "kuhhandel.cluster.secret must be set when cluster peers are configured"
        }
    }

    // "an", wenn Peers (oder peerService) UND ein Secret konfiguriert sind. Ergibt sich rein aus
    // der Config - hängt NICHT davon ab, ob andere Pods gerade laufen.
    val clusterEnabled: Boolean
        get() = (effectivePeers.isNotEmpty() || peerService.isNotBlank()) && secret.isNotBlank()
}
