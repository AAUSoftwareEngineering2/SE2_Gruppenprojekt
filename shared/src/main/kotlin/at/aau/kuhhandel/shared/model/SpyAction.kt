package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SpyAction(
    val spyId: String,
    val targetId: String,
    val expiresAt: Long,
    val revealedCards: Set<MoneyCard>,
)
