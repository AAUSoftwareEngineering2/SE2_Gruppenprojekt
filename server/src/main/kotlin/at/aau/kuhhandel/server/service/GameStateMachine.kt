package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState
import at.aau.kuhhandel.shared.model.TradeState

class GameStateMachine {
    fun apply(
        state: GameState,
        command: GameCommand,
    ): GameState =
        when (command) {
            GameCommand.StartGame -> startGame(state)
            GameCommand.RevealCard -> revealCard(state)
            GameCommand.ChooseAuction -> chooseAuction(state)
            is GameCommand.PlaceBid -> placeBid(state, command)
            GameCommand.CloseAuction -> closeAuction(state)
            is GameCommand.ResolveAuction -> resolveAuction(state, command)
            is GameCommand.ChooseTrade -> chooseTrade(state, command)
            GameCommand.FinishRound -> finishRound(state)
        }

    private fun startGame(state: GameState): GameState {
        check(state.phase == GamePhase.NOT_STARTED) {
            "Cannot start a game during phase ${state.phase}"
        }

        return state.copy(
            phase = GamePhase.PLAYER_TURN,
            roundNumber = 1,
            deck = createPrototypeDeck(),
            currentFaceUpCard = null,
            currentPlayerIndex = 0,
            auctionState = null,
            tradeState = null,
        )
    }

    private fun revealCard(state: GameState): GameState {
        check(state.phase == GamePhase.PLAYER_TURN) {
            "Cannot reveal a card during phase ${state.phase}"
        }

        if (state.deck.isEmpty()) {
            return state.copy(
                phase = GamePhase.FINISHED,
                currentFaceUpCard = null,
                auctionState = null,
                tradeState = null,
            )
        }

        val (nextCard, updatedDeck) = state.deck.drawTopCard()

        return state.copy(
            deck = updatedDeck,
            currentFaceUpCard = nextCard,
            auctionState = null,
            tradeState = null,
        )
    }

    private fun chooseAuction(state: GameState): GameState {
        check(state.phase == GamePhase.PLAYER_TURN) {
            "Cannot start an auction during phase ${state.phase}"
        }

        check(!state.deck.isEmpty()) {
            "Cannot start an auction without animal cards in the deck"
        }

        val (auctionCard, updatedDeck) = state.deck.drawTopCard()
        val activePlayer = requireActivePlayer(state)

        return state.copy(
            phase = GamePhase.AUCTION,
            deck = updatedDeck,
            currentFaceUpCard = null,
            auctionState =
                AuctionState(
                    auctionCard = requireNotNull(auctionCard),
                    auctioneerId = activePlayer.id,
                    timerEndTime = System.currentTimeMillis() + 5000,
                ),
            tradeState = null,
        )
    }

    private fun placeBid(
        state: GameState,
        command: GameCommand.PlaceBid,
    ): GameState {
        check(state.phase == GamePhase.AUCTION) {
            "Cannot place a bid during phase ${state.phase}"
        }

        val auctionState =
            requireActiveAuction(
                state,
                "Cannot place a bid without an active auction",
            )

        check(!auctionState.isClosed) {
            "Cannot place a bid after the auction is closed"
        }
        require(command.bidderId != auctionState.auctioneerId) {
            "Auctioneer cannot bid in their own auction"
        }
        require(state.players.any { it.id == command.bidderId }) {
            "Unknown bidder ${command.bidderId}"
        }
        require(command.amount > auctionState.highestBid) {
            "Bid must be higher than current highest bid"
        }

        return state.copy(
            auctionState =
                auctionState.copy(
                    highestBid = command.amount,
                    highestBidderId = command.bidderId,
                    timerEndTime = System.currentTimeMillis() + 5000,
                ),
        )
    }

    private fun closeAuction(state: GameState): GameState {
        check(state.phase == GamePhase.AUCTION) {
            "Cannot close an auction during phase ${state.phase}"
        }

        val auctionState = requireActiveAuction(state, "Cannot close without an active auction")

        check(!auctionState.isClosed) {
            "Auction is already closed"
        }

        return state.copy(
            auctionState = auctionState.copy(isClosed = true),
        )
    }

    private fun resolveAuction(
        state: GameState,
        command: GameCommand.ResolveAuction,
    ): GameState {
        check(state.phase == GamePhase.AUCTION) {
            "Cannot resolve an auction during phase ${state.phase}"
        }

        val auctionState = requireActiveAuction(state, "Cannot resolve without an active auction")

        check(auctionState.isClosed) {
            "Cannot resolve an auction before it is closed"
        }

        // If the auctioneer does not use their buy option, the highest bidder wins the card.
        val winnerId =
            if (!command.auctioneerBuysCard && auctionState.highestBidderId != null) {
                requireNotNull(auctionState.highestBidderId)
            } else {
                auctionState.auctioneerId
            }

        return state.copy(
            phase = GamePhase.ROUND_END,
            players = addAnimalToPlayer(state.players, winnerId, auctionState.auctionCard),
            currentFaceUpCard = null,
            auctionState = null,
            tradeState = null,
        )
    }

    private fun chooseTrade(
        state: GameState,
        command: GameCommand.ChooseTrade,
    ): GameState {
        check(state.phase == GamePhase.PLAYER_TURN) {
            "Cannot start a trade during phase ${state.phase}"
        }

        val activePlayer = requireActivePlayer(state)
        require(command.challengedPlayerId != activePlayer.id) {
            "Active player cannot challenge themselves"
        }

        val challengedPlayer =
            state.players.firstOrNull { it.id == command.challengedPlayerId }
                ?: throw IllegalArgumentException(
                    "Unknown challenged player ${command.challengedPlayerId}",
                )

        val requestedAnimalType = requireSharedAnimalType(activePlayer, challengedPlayer)

        return state.copy(
            phase = GamePhase.TRADE,
            auctionState = null,
            tradeState =
                TradeState(
                    initiatingPlayerId = activePlayer.id,
                    challengedPlayerId = challengedPlayer.id,
                    requestedAnimalType = requestedAnimalType,
                ),
        )
    }

    private fun finishRound(state: GameState): GameState {
        check(
            state.phase == GamePhase.AUCTION ||
                state.phase == GamePhase.TRADE ||
                state.phase == GamePhase.ROUND_END,
        ) {
            "Cannot finish a round during phase ${state.phase}"
        }

        if (state.players.isEmpty()) {
            return state.copy(
                phase = GamePhase.FINISHED,
                currentFaceUpCard = null,
                auctionState = null,
                tradeState = null,
            )
        }

        val nextPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
        val nextPhase = if (state.deck.isEmpty()) GamePhase.FINISHED else GamePhase.PLAYER_TURN

        return state.copy(
            phase = nextPhase,
            roundNumber = state.roundNumber + 1,
            currentPlayerIndex = nextPlayerIndex,
            currentFaceUpCard = null,
            auctionState = null,
            tradeState = null,
        )
    }

    private fun createPrototypeDeck(): AnimalDeck =
        AnimalDeck(
            listOf(
                AnimalCard(id = "1", type = AnimalType.COW),
                AnimalCard(id = "2", type = AnimalType.DOG),
                AnimalCard(id = "3", type = AnimalType.CAT),
            ),
        )

    private fun requireActivePlayer(state: GameState): PlayerState =
        state.players.getOrNull(state.currentPlayerIndex)
            ?: throw IllegalStateException("No active player at index ${state.currentPlayerIndex}")

    private fun requireActiveAuction(
        state: GameState,
        message: String,
    ): AuctionState =
        requireNotNull(state.auctionState) {
            message
        }

    private fun addAnimalToPlayer(
        players: List<PlayerState>,
        playerId: String,
        animalCard: AnimalCard,
    ): List<PlayerState> {
        require(players.any { it.id == playerId }) {
            "Unknown auction winner $playerId"
        }

        // GameState is immutable, so create an updated player list with the card added to the winner.
        return players.map { player ->
            if (player.id == playerId) {
                player.copy(animals = player.animals + animalCard)
            } else {
                player
            }
        }
    }

    private fun requireSharedAnimalType(
        activePlayer: PlayerState,
        challengedPlayer: PlayerState,
    ): AnimalType =
        activePlayer.animals
            .map { it.type }
            .firstOrNull { animalType -> challengedPlayer.animals.any { it.type == animalType } }
            ?: throw IllegalArgumentException(
                "Players ${activePlayer.id} and ${challengedPlayer.id} do not share an animal type",
            )
}
