package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val phase: GamePhase = GamePhase.NOT_STARTED,
    val roundNumber: Int = 0,
    val deck: AnimalDeck = AnimalDeck(),
    val currentFaceUpCard: AnimalCard? = null,
    val currentPlayerIndex: Int = -1,
    val players: List<Player> = emptyList(),
    val hostPlayerId: String? = null,
    // Active auction state, null if no auction is running
    val auctionState: AuctionState? = null,
    // Active trade state, null if no trade is running
    val tradeState: TradeState? = null,
    // The last event that occurred, e.g. a money bonus from a donkey
    val lastEvent: GameEvent? = null,
) {
    companion object {
        fun fromCreatingPlayer(
            id: String,
            name: String,
        ): GameState =
            GameState(
                phase = GamePhase.NOT_STARTED,
                roundNumber = 0,
                deck = AnimalDeck(),
                currentFaceUpCard = null,
                currentPlayerIndex = -1,
                players =
                    listOf(
                        Player(
                            id = id,
                            name = name,
                        ),
                    ),
                hostPlayerId = id,
                auctionState = null,
                tradeState = null,
            )
    }
}
