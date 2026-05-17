package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
sealed class GameEvent {
    @Serializable
    data class MoneyBonus(
        val amount: Int,
        val message: String
    ) : GameEvent()
}
