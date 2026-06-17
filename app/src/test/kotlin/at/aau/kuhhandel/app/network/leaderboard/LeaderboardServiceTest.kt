package at.aau.kuhhandel.app.network.leaderboard

import at.aau.kuhhandel.shared.model.GlobalLeaderboardItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaderboardServiceTest {
    @Test
    fun `fetchTopScores returns success with items when server responds 200`() {
        runBlocking {
            val mockData =
                listOf(
                    GlobalLeaderboardItem(1, "Player1", 100, 4, 500),
                    GlobalLeaderboardItem(2, "Player2", 80, 3, 300),
                )
            val jsonBody = Json.encodeToString(mockData)

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = jsonBody,
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
            val service = LeaderboardService(client)

            val result = service.fetchTopScores()

            assertTrue(result.isSuccess)
            assertEquals(mockData, result.getOrNull())
        }
    }

    @Test
    fun `fetchTopScores returns failure when server responds 500`() {
        runBlocking {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val client = HttpClient(mockEngine)
            val service = LeaderboardService(client)

            val result = service.fetchTopScores()

            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `fetchTopScores returns failure when network exception occurs`() {
        runBlocking {
            val mockEngine =
                MockEngine { _ ->
                    throw Exception("Connection timeout")
                }
            val client = HttpClient(mockEngine)
            val service = LeaderboardService(client)

            val result = service.fetchTopScores()

            assertTrue(result.isFailure)
        }
    }
}
