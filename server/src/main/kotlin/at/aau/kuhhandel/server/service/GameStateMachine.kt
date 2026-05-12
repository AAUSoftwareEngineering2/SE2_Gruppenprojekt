package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.enums.TradeStep
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.PlayerState
import at.aau.kuhhandel.shared.model.TradeState

class GameStateMachine {
    fun apply(
        state: GameState,
        command: GameCommand,
    ): GameState =
        when (command) {
            is GameCommand.AddPlayer -> addPlayer(state, command)
            is GameCommand.RemovePlayer -> removePlayer(state, command)
            GameCommand.StartGame -> startGame(state)
            GameCommand.RevealCard -> revealCard(state)
            GameCommand.ChooseAuction -> chooseAuction(state)
            is GameCommand.PlaceBid -> placeBid(state, command)
            GameCommand.CloseAuction -> closeAuction(state)
            is GameCommand.ResolveAuction -> resolveAuction(state, command)
            is GameCommand.ChooseTrade -> chooseTrade(state, command)
            is GameCommand.OfferTrade -> offerTrade(state, command)
            is GameCommand.RespondToTrade -> respondToTrade(state, command)
            GameCommand.FinishRound -> finishRound(state)
        }

    private fun addPlayer(
        state: GameState,
        command: GameCommand.AddPlayer,
    ): GameState {
        check(state.phase == GamePhase.NOT_STARTED) {
            "Cannot add a player during phase ${state.phase}"
        }

        check(state.players.none { it.id == command.playerId }) {
            "The player with ID ${command.playerId} is already in the game"
        }

        val player = PlayerState(command.playerId, command.playerName)

        return state.copy(
            players = state.players + player,
            hostPlayerId = state.hostPlayerId ?: player.id,
        )
    }

    private fun removePlayer(
        state: GameState,
        command: GameCommand.RemovePlayer,
    ): GameState {
        check(state.phase == GamePhase.NOT_STARTED) {
            "Cannot remove a player during phase ${state.phase}"
        }

        val newPlayers = state.players.filterNot { it.id == command.playerId }

        check(newPlayers.size < state.players.size) {
            "The player with ID ${command.playerId} is not in the game"
        }

        val newHostPlayerId =
            if (command.playerId == state.hostPlayerId) {
                newPlayers.firstOrNull()?.id
            } else {
                state.hostPlayerId
            }

        return state.copy(
            players = newPlayers,
            hostPlayerId = newHostPlayerId,
        )
    }

    private fun startGame(state: GameState): GameState {
        check(state.phase == GamePhase.NOT_STARTED) {
            "Cannot start a game during phase ${state.phase}"
        }

        return state.copy(
            phase = GamePhase.PLAYER_TURN,
            roundNumber = 1,
            deck = createInitialDeck(),
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
        val offeredMoneyCards = requireMoneyCards(activePlayer, command.offeredMoneyCardIds)

        return state.copy(
            phase = GamePhase.TRADE,
            auctionState = null,
            tradeState =
                TradeState(
                    initiatingPlayerId = activePlayer.id,
                    challengedPlayerId = challengedPlayer.id,
                    requestedAnimalType = requestedAnimalType,
                    step = TradeStep.WAITING_FOR_RESPONSE,
                    offeredMoney = offeredMoneyCards.sumOf { it.value },
                    offeredMoneyCardIds = command.offeredMoneyCardIds,
                    offeredMoneyCardCount = command.offeredMoneyCardIds.size,
                ),
        )
    }

    private fun offerTrade(
        state: GameState,
        command: GameCommand.OfferTrade,
    ): GameState {
        check(state.phase == GamePhase.TRADE) {
            "Cannot offer money for a trade during phase ${state.phase}"
        }

        val tradeState = requireActiveTrade(state, "Cannot offer money without an active trade")
        check(tradeState.step == TradeStep.WAITING_FOR_RESPONSE && !tradeState.isResolved) {
            "Cannot modify the offer after the trade is resolved"
        }
        require(command.offeredMoneyCardIds.isNotEmpty()) {
            "Trade offer must include at least one money card"
        }

        val initiator = requirePlayer(state.players, tradeState.initiatingPlayerId)
        val offeredCards = requireMoneyCards(initiator, command.offeredMoneyCardIds)

        return state.copy(
            tradeState =
                tradeState.copy(
                    offeredMoney = offeredCards.sumOf { it.value },
                    offeredMoneyCardIds = command.offeredMoneyCardIds,
                    offeredMoneyCardCount = command.offeredMoneyCardIds.size,
                ),
        )
    }

    private fun respondToTrade(
        state: GameState,
        command: GameCommand.RespondToTrade,
    ): GameState {
        check(state.phase == GamePhase.TRADE) {
            "Cannot respond to a trade during phase ${state.phase}"
        }

        val tradeState = requireActiveTrade(state, "Cannot respond without an active trade")
        check(tradeState.step == TradeStep.WAITING_FOR_RESPONSE && !tradeState.isResolved) {
            "Trade is already resolved"
        }
        require(command.respondingPlayerId == tradeState.challengedPlayerId) {
            "Only challenged player can respond to the trade"
        }

        if (!command.acceptsOffer && command.counterOfferedMoneyCardIds.isEmpty()) {
            return state.copy(
                phase = GamePhase.ROUND_END,
                currentFaceUpCard = null,
                auctionState = null,
                tradeState = null,
            )
        }

        require(tradeState.offeredMoneyCardIds.isNotEmpty()) {
            "Cannot accept or counter a trade before the initiator has placed a money offer"
        }

        val initiatingPlayer = requirePlayer(state.players, tradeState.initiatingPlayerId)
        val challengedPlayer = requirePlayer(state.players, tradeState.challengedPlayerId)
        val offeredMoneyCards = requireMoneyCards(initiatingPlayer, tradeState.offeredMoneyCardIds)
        val counterOfferCards =
            if (command.acceptsOffer) {
                emptyList()
            } else {
                requireMoneyCards(challengedPlayer, command.counterOfferedMoneyCardIds)
            }

        // Acceptance skips the counter-offer comparison; otherwise higher money wins the animal type.
        val initiatingPlayerWins =
            command.acceptsOffer ||
                offeredMoneyCards.sumOf { it.value } >= counterOfferCards.sumOf { it.value }
        val winnerId =
            if (initiatingPlayerWins) {
                tradeState.initiatingPlayerId
            } else {
                tradeState.challengedPlayerId
            }
        val loserId =
            if (initiatingPlayerWins) {
                tradeState.challengedPlayerId
            } else {
                tradeState.initiatingPlayerId
            }

        var updatedPlayers =
            moveAnimalTypeBetweenPlayers(
                players = state.players,
                fromPlayerId = loserId,
                toPlayerId = winnerId,
                animalType = tradeState.requestedAnimalType,
            )

        // When both players placed money, Kuhhandel swaps the committed money cards.
        updatedPlayers =
            transferMoneyCards(
                players = updatedPlayers,
                fromPlayerId = tradeState.initiatingPlayerId,
                toPlayerId = tradeState.challengedPlayerId,
                moneyCardIds = tradeState.offeredMoneyCardIds,
            )
        updatedPlayers =
            transferMoneyCards(
                players = updatedPlayers,
                fromPlayerId = tradeState.challengedPlayerId,
                toPlayerId = tradeState.initiatingPlayerId,
                moneyCardIds = counterOfferCards.map { it.id },
            )

        return state.copy(
            phase = GamePhase.ROUND_END,
            players = updatedPlayers,
            currentFaceUpCard = null,
            auctionState = null,
            tradeState = null,
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

    private fun createInitialDeck(): AnimalDeck =
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

    private fun requireActiveTrade(
        state: GameState,
        message: String,
    ): TradeState =
        requireNotNull(state.tradeState) {
            message
        }

    private fun requirePlayer(
        players: List<PlayerState>,
        playerId: String,
    ): PlayerState =
        players.firstOrNull { it.id == playerId }
            ?: throw IllegalArgumentException("Unknown player $playerId")

    private fun requireMoneyCards(
        player: PlayerState,
        moneyCardIds: List<String>,
    ): List<MoneyCard> {
        require(moneyCardIds.distinct().size == moneyCardIds.size) {
            "Money cards cannot be used more than once"
        }

        return moneyCardIds.map { moneyCardId ->
            player.moneyCards.firstOrNull { it.id == moneyCardId }
                ?: throw IllegalArgumentException(
                    "Player ${player.id} does not own money card $moneyCardId",
                )
        }
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

    private fun moveAnimalTypeBetweenPlayers(
        players: List<PlayerState>,
        fromPlayerId: String,
        toPlayerId: String,
        animalType: AnimalType,
    ): List<PlayerState> {
        val animalCardsToMove =
            requirePlayer(players, fromPlayerId)
                .animals
                .filter { it.type == animalType }

        require(animalCardsToMove.isNotEmpty()) {
            "Player $fromPlayerId does not own animal type $animalType"
        }

        return players.map { player ->
            when (player.id) {
                fromPlayerId ->
                    player.copy(
                        animals =
                            player.animals.filterNot { animalCard ->
                                animalCardsToMove.any { it.id == animalCard.id }
                            },
                    )
                toPlayerId -> player.copy(animals = player.animals + animalCardsToMove)
                else -> player
            }
        }
    }

    private fun transferMoneyCards(
        players: List<PlayerState>,
        fromPlayerId: String,
        toPlayerId: String,
        moneyCardIds: List<String>,
    ): List<PlayerState> {
        if (moneyCardIds.isEmpty()) {
            return players
        }

        val moneyCardsToTransfer =
            requireMoneyCards(
                requirePlayer(players, fromPlayerId),
                moneyCardIds,
            )

        return players.map { player ->
            when (player.id) {
                fromPlayerId ->
                    player.copy(
                        moneyCards =
                            player.moneyCards.filterNot { moneyCard ->
                                moneyCardsToTransfer.any { it.id == moneyCard.id }
                            },
                    )
                toPlayerId -> player.copy(moneyCards = player.moneyCards + moneyCardsToTransfer)
                else -> player
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
