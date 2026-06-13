package at.aau.kuhhandel.server.cluster

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClusterUpdateNotifierTest {
    private data class RecordedRequest(
        val secret: String?,
        val origin: String?,
        val body: String,
    )

    @Test
    fun `gameUpdated does nothing when the cluster is disabled`() {
        // No peers + no secret -> single-pod mode, must be a no-op (no exception, no HTTP).
        ClusterUpdateNotifier(ClusterProperties()).gameUpdated("12345")
    }

    @Test
    fun `gameUpdated posts the notification to a static peer`() {
        val received = CompletableFuture<RecordedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/internal/cluster/game-updated") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            val secret = exchange.requestHeaders.getFirst(ClusterUpdateNotifier.SECRET_HEADER)
            val origin = exchange.requestHeaders.getFirst(ClusterUpdateNotifier.ORIGIN_HEADER)
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            received.complete(RecordedRequest(secret, origin, body))
        }
        server.start()

        try {
            val notifier =
                ClusterUpdateNotifier(
                    ClusterProperties(
                        peers = listOf("http://127.0.0.1:${server.address.port}"),
                        secret = "s3cret",
                    ),
                )

            notifier.gameUpdated("12345")

            val request = received.get(5, TimeUnit.SECONDS)
            assertEquals("s3cret", request.secret)
            assertEquals(notifier.instanceId, request.origin)
            assertTrue(
                request.body.contains("12345"),
                "body should carry the game id: ${request.body}",
            )
        } finally {
            server.stop(0)
        }
    }
}
