package at.aau.kuhhandel.server.api

import at.aau.kuhhandel.server.persistence.repository.LeaderboardRepository
import at.aau.kuhhandel.shared.ApiRoutes
import at.aau.kuhhandel.shared.model.GlobalLeaderboardItem
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class LeaderboardController(
    private val repository: LeaderboardRepository,
) {
    /**
     * Exposes the global leaderboard endpoint.
     */
    @GetMapping(ApiRoutes.LEADERBOARD)
    fun getLeaderboard(): List<GlobalLeaderboardItem> {
        val dbEntries = repository.findTop10ByOrderByScoreDescTotalMoneyDesc()

        return dbEntries.mapIndexed { index, entry ->
            GlobalLeaderboardItem(
                rank = index + 1,
                playerName = entry.playerName,
                score = entry.score,
                quartetCount = entry.quartetCount,
                totalMoney = entry.totalMoney,
            )
        }
    }
}
