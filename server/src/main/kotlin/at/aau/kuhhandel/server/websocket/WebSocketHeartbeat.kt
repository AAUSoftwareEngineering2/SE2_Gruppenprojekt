package at.aau.kuhhandel.server.websocket

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Sends a WebSocket ping frame to every open session every ~25s.
 *
 * Why: the path Client → Cloudflare → Caddy → Tomcat has a ~100s idle timeout at the Cloudflare
 * edge (free tier). The Android client already pings every 20s via OkHttp; this symmetric
 * server-side ping does two things:
 *   1. Detects dead sessions earlier (`session.sendMessage` throws on a half-open connection),
 *      so we can clean them up via ConnectionRegistry instead of waiting for the next user action.
 *   2. Keeps the edge from closing the connection during a quiet phase of the game.
 *
 * Dead sessions are unbound from the registry so subsequent broadcasts do not target them.
 */
@Component
class WebSocketHeartbeat(
    private val connectionRegistry: ConnectionRegistry,
) {
    private val logger = LoggerFactory.getLogger(WebSocketHeartbeat::class.java)

    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS, initialDelay = HEARTBEAT_INTERVAL_MS)
    fun sendHeartbeats() {
        connectionRegistry.allSessions().forEach { session ->
            pingOrEvict(session)
        }
    }

    private fun pingOrEvict(session: WebSocketSession) {
        if (!session.isOpen) {
            connectionRegistry.unbind(session.id)
            return
        }
        try {
            synchronized(session) {
                if (session.isOpen) {
                    session.sendMessage(PingMessage(EMPTY_PAYLOAD))
                }
            }
        } catch (e: IOException) {
            logger.debug("Heartbeat failed for session {} — evicting: {}", session.id, e.message)
            connectionRegistry.unbind(session.id)
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private val EMPTY_PAYLOAD: ByteBuffer = ByteBuffer.allocate(0)
    }
}
