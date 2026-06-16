package at.aau.kuhhandel.app.network.ping

import at.aau.kuhhandel.app.network.ApiConfig
import at.aau.kuhhandel.shared.ApiRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess

/** Service used to check the availability of the game server. */
class PingService(
    private val client: HttpClient,
) {
    suspend fun isServerReachable(): Result<Boolean> =
        try {
            val response = client.get("${ApiConfig.HTTP_URL}${ApiRoutes.HEALTH}")
            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                Result.failure(Exception("Server returned ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
}
