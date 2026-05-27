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
    fun createViewForPlayer(playerId: String): GameStateView {
        val localPlayer = this.players.find { it.id == playerId }
        checkNotNull(localPlayer) {
            "Viewing player $playerId not found in game state"
        }

        val opponents =
            this.players
                .filter { it.id != playerId }
                .map { player ->
                    Opponent(
                        id = player.id,
                        name = player.name,
                        animals = player.animals,
                        moneyCardCount = player.moneyCards.size,
                    )
                }

        val tradeStateView =
            this.tradeState?.let { tradeState ->
                val isRevealPhase = this.phase == GamePhase.TRADE_REVEAL

                val initiatorCards =
                    if (isRevealPhase || playerId == tradeState.initiatorId) {
                        tradeState.offeredMoneyCards
                    } else {
                        null
                    }

                val targetCards =
                    if (isRevealPhase) {
                        tradeState.counterOfferedMoneyCards
                    } else {
                        null
                    }

                TradeStateView(
                    initiatorId = tradeState.initiatorId,
                    targetId = tradeState.targetId,
                    requestedAnimalType = tradeState.requestedAnimalType,
                    initiatorCardCount = tradeState.offeredMoneyCards.size,
                    targetCardCount = targetCards?.size,
                    visibleInitiatorCards = initiatorCards?.toList(),
                    visibleTargetCards = targetCards?.toList(),
                )
            }

        return GameStateView(
            phase = this.phase,
            localPlayer = localPlayer,
            opponents = opponents,
            hostPlayerId = checkNotNull(this.hostPlayerId) { "Game state has no host" },
            roundNumber = this.roundNumber,
            currentPlayerIndex = this.currentPlayerIndex,
            deckSize = this.deck.size(),
            auctionState = this.auctionState,
            tradeState = tradeStateView,
            lastEvent = this.lastEvent,
        )
    }

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
