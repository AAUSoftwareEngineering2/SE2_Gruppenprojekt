package at.aau.kuhhandel.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class PingServiceTest {
    @Test
    fun `isServerReachable returns success when server responds 200`() =
        runBlocking {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "OK",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json()
                    }
                }
            val service = PingService(client)

            val result = service.isServerReachable()

            assertTrue(result.isSuccess)
        }

    @Test
    fun `isServerReachable returns failure when server responds 500`() =
        runBlocking {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val client = HttpClient(mockEngine)
            val service = PingService(client)

            val result = service.isServerReachable()

            assertTrue(result.isFailure)
        }

    @Test
    fun `isServerReachable returns failure when exception is thrown`() =
        runBlocking {
            val mockEngine =
                MockEngine { _ ->
                    throw Exception("Network error")
                }
            val client = HttpClient(mockEngine)
            val service = PingService(client)

            val result = service.isServerReachable()

            assertTrue(result.isFailure)
        }
}
