package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
sealed class GameEvent {
    abstract val message: String

    @Serializable
    data class MoneyBonus(
        val amount: Int,
        override val message: String,
    ) : GameEvent()

    @Serializable
    data class BluffDetected(
        val playerId: String,
        val playerName: String,
        override val message: String,
    ) : GameEvent()
}
