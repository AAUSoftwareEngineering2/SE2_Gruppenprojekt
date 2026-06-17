package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.utils.GameRankEntry
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val phase: GamePhase = GamePhase.NOT_STARTED,
    val timerEnd: Long? = null,
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
    val activeSpies: Set<SpyAction> = emptySet(),
    val spiedThisTurn: Set<String> = emptySet(),
    // The last event that occurred, e.g. a money bonus from a donkey
    val lastEvent: GameEvent? = null,
    val finalRanking: List<GameRankEntry>? = null,
) {
    fun createViewForPlayer(playerId: String): GameStateView {
        val localPlayer = this.players.find { it.id == playerId }
        checkNotNull(localPlayer) {
            "Viewing player $playerId not found in game state"
        }

        // Order opponents according to the turn order, with the first opponent
        // in the list having their turn directly after the local player
        val localPlayerIndex = this.players.indexOf(localPlayer)
        val opponents =
            (1 until this.players.size).map { offset ->
                val targetIndex = (localPlayerIndex + offset) % this.players.size
                val player = this.players[targetIndex]

                Opponent(
                    id = player.id,
                    name = player.name,
                    animals = player.animals,
                    moneyCardCount = player.moneyCards.size,
                    isConnected = player.isConnected,
                )
            }

        val tradeStateView =
            this.tradeState?.let { tradeState ->
                val isResultPhase = this.phase == GamePhase.TRADE_RESULT

                val initiatorCards =
                    if (isResultPhase || playerId == tradeState.initiatorId) {
                        tradeState.offeredMoneyCards
                    } else {
                        null
                    }

                val targetCards =
                    if (isResultPhase) {
                        tradeState.counterOfferedMoneyCards
                    } else {
                        null
                    }

                TradeStateView(
                    initiatorId = tradeState.initiatorId,
                    targetId = tradeState.targetId,
                    animalCards = tradeState.animalCards.toList(),
                    initiatorCardCount = tradeState.offeredMoneyCards?.size,
                    targetCardCount = tradeState.counterOfferedMoneyCards?.size,
                    visibleInitiatorCards = initiatorCards?.toList(),
                    visibleTargetCards = targetCards?.toList(),
                    winnerId = tradeState.winnerId,
                )
            }

        val alreadySpied = this.spiedThisTurn.contains(playerId)
        val activeLocalCheating = this.activeSpies.find { it.spyId == playerId }
        val localPlayerSpiedOn = this.activeSpies.any { it.targetId == playerId }
        val spiedOnOpponentIds =
            this.activeSpies
                .filterNot { it.spyId == playerId || it.targetId == playerId }
                .map { it.targetId }

        return GameStateView(
            phase = this.phase,
            timerEnd = this.timerEnd,
            localPlayer = localPlayer,
            opponents = opponents,
            hostPlayerId = checkNotNull(this.hostPlayerId) { "Game state has no host" },
            roundNumber = this.roundNumber,
            currentPlayerId = this.players.getOrNull(this.currentPlayerIndex)?.id,
            deckSize = this.deck.size(),
            auctionState = this.auctionState,
            tradeState = tradeStateView,
            alreadySpied = alreadySpied,
            spyingTargetId = activeLocalCheating?.targetId,
            spyingTargetCards = activeLocalCheating?.revealedCards?.toList(),
            localPlayerSpiedOn = localPlayerSpiedOn,
            spiedOnOpponentIds = spiedOnOpponentIds,
            finalRanking = this.finalRanking,
            lastEvent = this.lastEvent,
        )
    }

    fun updatePlayer(
        playerId: String,
        transform: (Player) -> Player,
    ): GameState {
        var playerFound = false
        val updatedPlayers =
            this.players.map { player ->
                if (player.id == playerId) {
                    playerFound = true
                    transform(player)
                } else {
                    player
                }
            }
        check(playerFound) { "Player $playerId not found to update" }
        return this.copy(players = updatedPlayers)
    }

    companion object {
        fun fromCreatingPlayer(
            id: String,
            name: String,
        ): GameState =
            GameState(
                phase = GamePhase.NOT_STARTED,
                timerEnd = null,
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

    fun isFinished(): Boolean = this.phase == GamePhase.FINISHED

    fun hasPlayer(playerId: String): Boolean = this.players.any { it.id == playerId }

    fun hasNoPlayers(): Boolean = this.players.isEmpty()
}
