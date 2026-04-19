package at.aau.kuhhandel.app.network

import at.aau.kuhhandel.shared.ApiRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess

class PingService(
    private val client: HttpClient = NetworkClientFactory.create(),
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
        } finally {
            client.close()
        }
}
