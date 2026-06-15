package at.aau.kuhhandel.shared.utils

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.Player
import kotlinx.serialization.Serializable

/**
 * Result of a player's performance in a finished game.
 */
@Serializable
data class GameRankEntry(
    val playerId: String,
    val playerName: String,
    val points: Int,
    val quartetCount: Int,
    val totalMoney: Int,
    val isWinner: Boolean = false,
    val collectedAnimalTypes: List<AnimalType> = emptyList(),
)

/**
 * Utility for calculating scores and determining the winner.
 */
object ScoreCalculator {
    /**
     * Calculates the score for a single player.
     * Formula: (Sum of quartet values) * (Number of quartets)
     */
    fun calculateScore(player: Player): Int {
        val quartets = getFullQuartets(player)
        if (quartets.isEmpty()) return 0
        val sum = quartets.sumOf { it.points }
        return sum * quartets.size
    }

    /**
     * Returns a list of AnimalTypes for which the player has a full quartet (4 cards).
     */
    fun getFullQuartets(player: Player): List<AnimalType> =
        player.animals
            .groupBy { it.type }
            .filter { it.value.size == 4 }
            .keys
            .toList()

    /**
     * Calculates and sorts the ranking for a list of players.
     * Sort priority: Points (DESC), Total Money (DESC).
     */
    fun calculateGameRanking(players: List<Player>): List<GameRankEntry> {
        val results =
            players
                .map { player ->
                    val points = calculateScore(player)
                    val quartets = getFullQuartets(player)
                    GameRankEntry(
                        playerId = player.id,
                        playerName = player.name,
                        points = points,
                        quartetCount = quartets.size,
                        totalMoney = player.totalMoney(),
                        collectedAnimalTypes = quartets,
                    )
                }.sortedWith(
                    compareByDescending<GameRankEntry> { it.points }
                        .thenByDescending { it.totalMoney },
                )

        if (results.isEmpty()) return emptyList()

        // Mark the first one as the winner (or multiple if exact same points and money)
        val winnerPoints = results.first().points
        val winnerMoney = results.first().totalMoney

        return results.map { res ->
            res.copy(isWinner = res.points == winnerPoints && res.totalMoney == winnerMoney)
        }
    }
}
