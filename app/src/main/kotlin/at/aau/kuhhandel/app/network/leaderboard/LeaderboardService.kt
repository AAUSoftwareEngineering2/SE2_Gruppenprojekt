package at.aau.kuhhandel.app.network.leaderboard

import at.aau.kuhhandel.app.network.ApiConfig
import at.aau.kuhhandel.shared.ApiRoutes
import at.aau.kuhhandel.shared.model.GlobalLeaderboardItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Service used to fetch the top global player rankings. */
class LeaderboardService(
    private val client: HttpClient,
) {
    /** Fetches the top recorded scores. */
    suspend fun fetchTopScores(): Result<List<GlobalLeaderboardItem>> =
        runCatching {
            client.get("${ApiConfig.HTTP_URL}${ApiRoutes.LEADERBOARD}").body()
        }
}
