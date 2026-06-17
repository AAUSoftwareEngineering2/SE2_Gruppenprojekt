package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class GlobalLeaderboardItem(
    val rank: Int,
    val playerName: String,
    val score: Int,
    val quartetCount: Int,
    val totalMoney: Int,
)
