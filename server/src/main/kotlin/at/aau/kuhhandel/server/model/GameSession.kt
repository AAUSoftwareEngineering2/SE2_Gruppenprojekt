package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameEvent
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.PhaseDurations
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.model.SpyAction
import at.aau.kuhhandel.shared.model.TradeState
import at.aau.kuhhandel.shared.utils.ScoreCalculator

/**
 * State machine representing a single game session.
 */
class GameSession(
    val gameId: String,
    hostPlayerId: String,
    hostPlayerName: String,
    initialState: GameState? = null,
) {
    // Each session manages its own current game state
    var state: GameState =
        initialState ?: GameState.fromCreatingPlayer(hostPlayerId, hostPlayerName)
        private set

    /**
     * Adds a player to the game session.
     */
    fun addPlayer(
        playerId: String,
        playerName: String,
    ): GameState {
        ensurePlayerNotInRoom(playerId)
        ensurePhase(GamePhase.NOT_STARTED)
        ensureRoomNotFull()

        val player = Player(playerId, playerName)

        state =
            state.copy(
                players = state.players + player,
                hostPlayerId = state.hostPlayerId ?: player.id,
            )

        return state
    }

    /**
     * Removes a player from the game session.
     */
    fun removePlayer(playerId: String): GameState {
        ensureActorInRoom(playerId)
        ensurePhase(GamePhase.NOT_STARTED)

        val newPlayers = state.players.filterNot { it.id == playerId }

        val newHostPlayerId =
            if (playerId == state.hostPlayerId) {
                newPlayers.firstOrNull()?.id
            } else {
                state.hostPlayerId
            }

        state =
            state.copy(
                players = newPlayers,
                hostPlayerId = newHostPlayerId,
            )

        return state
    }

    /**
     * Starts a game with a simple initial deck.
     */
    fun startGame(actorId: String): GameState {
        ensureActorInRoom(actorId)
        ensureHost(actorId)
        ensurePhase(GamePhase.NOT_STARTED)
        ensureEnoughPlayers()

        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.PLAYER_CHOICE_MS

        state =
            state.copy(
                phase = GamePhase.PLAYER_CHOICE,
                timerEnd = calculatedTimeout,
                roundNumber = 1,
                deck = createInitialDeck(),
                players =
                    state.players.shuffled().map { player ->
                        player.copy(
                            animals = emptyList(),
                            moneyCards = createInitialMoney(player.id),
                        )
                    },
                currentPlayerIndex = 0,
            )

        return state
    }

    /**
     * Initiates an auction by drawing the top card from the animal deck.
     */
    fun chooseAuction(actorId: String): GameState {
        ensureActorInRoom(actorId)
        ensurePhase(GamePhase.PLAYER_CHOICE)
        ensureActivePlayer(actorId)
        ensureDeckNotEmpty()

        val (auctionCard, updatedDeck) = state.deck.drawTopCard()
        val card =
            checkNotNull(auctionCard) {
                "Drawn card is null even though deck was not empty"
            }

        var updatedPlayers = state.players
        var event: GameEvent? = null

        if (card.type == AnimalType.DONKEY) {
            val donkeysInDeckAfterDraw = updatedDeck.cards.count { it.type == AnimalType.DONKEY }
            val donkeyNumber = CARDS_PER_ANIMAL_TYPE - donkeysInDeckAfterDraw

            val bonusValue =
                when (donkeyNumber) {
                    1 -> 50
                    2 -> 100
                    3 -> 200
                    4 -> 500
                    else -> 0
                }

            if (bonusValue > 0) {
                updatedPlayers =
                    state.players.map { player ->
                        val newMoneyCard =
                            MoneyCard(
                                id = "${player.id}-donkey-$donkeyNumber",
                                value = bonusValue,
                            )
                        player.copy(moneyCards = player.moneyCards + newMoneyCard)
                    }
                event =
                    GameEvent.MoneyBonus(
                        amount = bonusValue,
                        message = "Goldesel! Jeder erhält $bonusValue€.",
                    )
            }
        }

        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.AUCTION_BIDDING_MS

        state =
            state.copy(
                phase = GamePhase.AUCTION_BIDDING,
                timerEnd = calculatedTimeout,
                deck = updatedDeck,
                players = updatedPlayers,
                lastEvent = event,
                auctionState =
                    AuctionState(
                        auctionCard = card,
                        auctioneerId = actorId,
                        highestBid = 0,
                        highestBidderId = null,
                        timerEndTime = calculatedTimeout,
                    ),
                activeSpies = emptySet(),
                spiedThisTurn = emptySet(),
            )

        return state
    }

    /**
     * Places a bid on the active auction.
     */
    fun placeBid(
        actorId: String,
        amount: Int,
    ): GameState {
        ensureActorInRoom(actorId)
        ensurePhase(GamePhase.AUCTION_BIDDING)
        val auctionState =
            checkNotNull(state.auctionState) {
                "Missing auction state in bidding phase"
            }
        ensureNotAuctioneer(auctionState, actorId)
        ensureNotExcludedFromAuction(auctionState, actorId)
        ensureBidNotTooLow(auctionState, amount)
        ensureBidNotTooHigh(amount)

        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.AUCTION_BIDDING_MS

        state =
            state.copy(
                timerEnd = calculatedTimeout,
                lastEvent = null,
                auctionState =
                    auctionState.copy(
                        highestBid = amount,
                        highestBidderId = actorId,
                        timerEndTime = calculatedTimeout,
                    ),
            )

        return state
    }

    /**
     * Resolves the current auction phase based on the auctioneer's buy-back choice.
     */
    fun resolveAuction(
        actorId: String,
        auctioneerBuysCard: Boolean,
    ): GameState {
        val actor = requireActorInRoom(actorId)
        ensurePhase(GamePhase.AUCTIONEER_DECISION)
        val auctionState =
            checkNotNull(state.auctionState) {
                "Missing auction state in resolution phase"
            }
        ensureAuctioneer(auctionState, actorId)

        val highestBidderId =
            checkNotNull(auctionState.highestBidderId) {
                "Missing highest bidder in auction resolution"
            }
        val highestBidder =
            checkNotNull(state.players.find { it.id == highestBidderId }) {
                "Highest bidder $highestBidderId not found among players"
            }

        // Determine the card receiver and seller
        val receiver: Player
        val seller: Player

        if (auctioneerBuysCard) {
            ensureHasEnoughMoney(actor, auctionState.highestBid)
            receiver = actor
            seller = highestBidder
        } else {
            // Bluff Check: If the winner cannot pay the auctioneer, they bluffed
            if (highestBidder.totalMoney() < auctionState.highestBid) {
                val calculatedTimeout =
                    System.currentTimeMillis() + PhaseDurations.AUCTION_BIDDING_MS

                state =
                    state.copy(
                        phase = GamePhase.AUCTION_BIDDING,
                        timerEnd = calculatedTimeout,
                        auctionState =
                            auctionState.copy(
                                highestBid = 0,
                                highestBidderId = null,
                                timerEndTime = calculatedTimeout,
                                excludedPlayerIds =
                                    auctionState.excludedPlayerIds + highestBidderId,
                            ),
                        lastEvent =
                            GameEvent.BluffDetected(
                                playerId = highestBidderId,
                                playerName = highestBidder.name,
                                message = "${highestBidder.name} bluffed! Auction restarts.",
                            ),
                    )

                return state
            }
            receiver = highestBidder
            seller = actor
        }

        // Process the payment and give the animal card to the buyer
        val paymentCardIds = selectMoneyCardsForPayment(receiver, auctionState.highestBid)
        val playersAfterPayment =
            transferMoneyCards(state.players, receiver, seller, paymentCardIds)
        val updatedPlayers =
            addAnimalToPlayer(playersAfterPayment, receiver.id, auctionState.auctionCard)

        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.AUCTION_RESULT_MS

        state =
            state.copy(
                phase = GamePhase.AUCTION_RESULT,
                timerEnd = calculatedTimeout,
                players = updatedPlayers,
                auctionState =
                    auctionState.copy(
                        timerEndTime = null,
                        buyerId = receiver.id,
                    ),
            )

        return state
    }

    /**
     * Initiates a trade by selecting a target player and an animal type.
     */
    fun chooseTrade(
        actorId: String,
        targetId: String,
        animalType: AnimalType,
    ): GameState {
        val initiator = requireActorInRoom(actorId)
        ensurePhase(GamePhase.PLAYER_CHOICE)
        ensureActivePlayer(actorId)
        val target = requireValidTarget(targetId)
        ensureNotTargetingSelf(actorId, targetId)
        val initiatorMatchingAnimals = requireTradeInitiatorHasAnimalType(initiator, animalType)
        val targetMatchingAnimals = requireTradeTargetHasAnimalType(target, animalType)

        // Determine whether to move one or two cards based on the counts owned by the players
        val animalsToMoveCount =
            if (initiatorMatchingAnimals.size >= 2 && targetMatchingAnimals.size >= 2) 2 else 1

        val initiatorAnimals = initiatorMatchingAnimals.take(animalsToMoveCount).toSet()
        val targetAnimals = targetMatchingAnimals.take(animalsToMoveCount).toSet()

        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.TRADE_OFFER_MS

        // Remove the animal cards from the players, transition
        // the phase, and store the trade information
        state =
            state
                .updatePlayer(initiator.id) { player ->
                    player.copy(animals = player.animals - initiatorAnimals)
                }.updatePlayer(target.id) { player ->
                    player.copy(animals = player.animals - targetAnimals)
                }.copy(
                    phase = GamePhase.TRADE_OFFER,
                    timerEnd = calculatedTimeout,
                    tradeState =
                        TradeState(
                            initiatorId = initiator.id,
                            targetId = target.id,
                            requestedAnimalType = animalType,
                            animalCards = initiatorAnimals + targetAnimals,
                            offeredMoneyCardIds = emptySet(),
                            counterOfferedMoneyCardIds = emptySet(),
                            offeredMoneyCards = null,
                            counterOfferedMoneyCards = null,
                        ),
                    activeSpies = emptySet(),
                    spiedThisTurn = emptySet(),
                )

        return state
    }

    /**
     * Submits the initiator's money cards for the trade.
     */
    fun submitTradeMoney(
        actorId: String,
        offeredMoneyCardIds: Set<String>,
    ): GameState {
        val initiator = requireActorInRoom(actorId)
        ensurePhase(GamePhase.TRADE_OFFER)
        val tradeState =
            checkNotNull(state.tradeState) {
                "Missing trade state in trade offer phase"
            }
        ensureTradeInitiator(tradeState, actorId)

        var offeredMoneyCards = emptySet<MoneyCard>()

        // If the trade offer is not empty
        if (offeredMoneyCardIds.isNotEmpty()) {
            offeredMoneyCards = requireOwnsMoneyCards(initiator, offeredMoneyCardIds)

            // Remove the money cards from the player safely via your utility extension
            state =
                state.updatePlayer(initiator.id) { player ->
                    player.copy(moneyCards = initiator.moneyCards - offeredMoneyCards)
                }
        }

        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.TRADE_RESPONSE_MS

        // Transition the phase and update the trade information
        state =
            state.copy(
                phase = GamePhase.TRADE_RESPONSE,
                timerEnd = calculatedTimeout,
                tradeState =
                    tradeState.copy(
                        offeredMoneyCardIds = offeredMoneyCardIds,
                        offeredMoney = offeredMoneyCards.sumOf { it.value },
                        offeredMoneyCards = offeredMoneyCards,
                    ),
            )

        return state
    }

    /**
     * Adds the trade target's response information to the game state,
     * with empty [counterOfferedMoneyCardIds] representing blind
     * acceptance, and calculates the result of the trade.
     */
    fun respondToTrade(
        actorId: String,
        counterOfferedMoneyCardIds: Set<String>,
    ): GameState {
        val target = requireActorInRoom(actorId)
        ensurePhase(GamePhase.TRADE_RESPONSE)
        val tradeState =
            checkNotNull(state.tradeState) {
                "Missing trade state in response phase"
            }
        ensureTradeTarget(tradeState, actorId)

        val offeredMoneyCards =
            checkNotNull(tradeState.offeredMoneyCards) {
                "Initiator offer missing in trade response"
            }

        var counterOfferedMoneyCards = emptySet<MoneyCard>()

        // If the trade target does not accept the trade blindly
        if (counterOfferedMoneyCardIds.isNotEmpty()) {
            counterOfferedMoneyCards = requireOwnsMoneyCards(target, counterOfferedMoneyCardIds)

            // Remove the money cards from the player
            state =
                state.updatePlayer(target.id) { player ->
                    player.copy(moneyCards = target.moneyCards - counterOfferedMoneyCards)
                }
        }

        val initiatorTotal = offeredMoneyCards.sumOf { it.value }
        val targetTotal = counterOfferedMoneyCards.sumOf { it.value }

        // Tie-breaker rule: initiator wins all ties
        val winnerId =
            if (initiatorTotal >= targetTotal) {
                tradeState.initiatorId
            } else {
                tradeState.targetId
            }

        // Process the transaction
        state =
            state
                .updatePlayer(tradeState.initiatorId) { player ->
                    val wonAnimals =
                        if (player.id ==
                            winnerId
                        ) {
                            tradeState.animalCards
                        } else {
                            emptySet()
                        }
                    player.copy(
                        moneyCards = player.moneyCards + counterOfferedMoneyCards,
                        animals = player.animals + wonAnimals,
                    )
                }.updatePlayer(tradeState.targetId) { player ->
                    val wonAnimals =
                        if (player.id ==
                            winnerId
                        ) {
                            tradeState.animalCards
                        } else {
                            emptySet()
                        }
                    player.copy(
                        moneyCards = player.moneyCards + offeredMoneyCards,
                        animals = player.animals + wonAnimals,
                    )
                }

        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.TRADE_RESULT_MS

        // Transition the phase and update the trade information
        state =
            state.copy(
                phase = GamePhase.TRADE_RESULT,
                timerEnd = calculatedTimeout,
                tradeState =
                    tradeState.copy(
                        counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
                        counterOfferedMoney = counterOfferedMoneyCards.sumOf { it.value },
                        counterOfferedMoneyCards = counterOfferedMoneyCards,
                        winnerId = winnerId,
                    ),
            )

        return state
    }

    /**
     * Starts spying on a chosen target player.
     */
    fun spy(
        actorId: String,
        targetId: String,
    ): GameState {
        val spy = requireActorInRoom(actorId)
        ensurePhase(GamePhase.PLAYER_CHOICE)
        ensureSpyNotActivePlayer(actorId)
        ensureNotSpiedThisTurn(actorId)
        val target = requireValidTarget(targetId)
        ensureNotTargetingSelf(actorId, targetId)
        ensureHasMoneyCardsForSpying(spy)

        val revealedCards =
            target.moneyCards
                .shuffled()
                .take(SPYING_CARDS_REVEALED)
                .toSet()

        val expiresAt = System.currentTimeMillis() + PhaseDurations.SPY_WINDOW_MS

        val newSpy =
            SpyAction(
                spyId = actorId,
                targetId = targetId,
                expiresAt = expiresAt,
                revealedCards = revealedCards,
            )

        state =
            state.copy(
                activeSpies = state.activeSpies + newSpy,
                spiedThisTurn = state.spiedThisTurn + actorId,
            )

        return state
    }

    /**
     * Catches all players currently spying on the actor.
     */
    fun catchSpy(actorId: String): GameState {
        requireActorInRoom(actorId)
        ensureNotSpying(actorId)
        val validSpies = requireValidSpies(actorId, System.currentTimeMillis())

        var updatedPlayers = state.players

        validSpies.forEach { spyAction ->
            val spy =
                checkNotNull(updatedPlayers.find { it.id == spyAction.spyId }) {
                    "Spy ${spyAction.spyId} not found among players when caught"
                }
            val penaltyCard =
                checkNotNull(spy.moneyCards.randomOrNull()) {
                    "Spy ${spy.id} has no money cards when caught"
                }

            updatedPlayers =
                updatedPlayers.map { player ->
                    when (player.id) {
                        spy.id -> player.copy(moneyCards = player.moneyCards - penaltyCard)
                        actorId -> player.copy(moneyCards = player.moneyCards + penaltyCard)
                        else -> player
                    }
                }
        }

        state =
            state.copy(
                players = updatedPlayers,
                activeSpies = state.activeSpies - validSpies.toSet(),
            )

        return state
    }

    /**
     * Transitions the game phase when a timer expires.
     */
    fun handleTimeoutExpiration(): GameState =
        when (state.phase) {
            GamePhase.PLAYER_CHOICE -> makeDefaultPlayerChoice()
            GamePhase.AUCTION_BIDDING -> closeAuctionBidding()
            GamePhase.AUCTIONEER_DECISION -> makeDefaultAuctioneerDecision()
            GamePhase.AUCTION_RESULT -> endAuctionSequence()
            GamePhase.TRADE_OFFER -> makeDefaultTradeOffer()
            GamePhase.TRADE_RESPONSE -> makeDefaultTradeResponse()
            GamePhase.TRADE_RESULT -> endTradeSequence()
            GamePhase.NOT_STARTED, GamePhase.FINISHED -> throw IllegalStateException(
                "Timeout handler triggered during an untimed phase: ${state.phase}",
            )
        }

    /**
     * Skip the player's turn.
     */
    private fun makeDefaultPlayerChoice(): GameState {
        state =
            state.copy(
                activeSpies = emptySet(),
                spiedThisTurn = emptySet(),
            )
        return advanceTurnAndCheckGameEnd()
    }

    /**
     * Concludes bidding and determines whether to show a final result or request a decision.
     */
    private fun closeAuctionBidding(): GameState {
        val auctionState =
            checkNotNull(state.auctionState) {
                "Missing auction state in bidding phase"
            }

        // If no one placed a bid, the auctioneer gets the card for free.
        if (auctionState.highestBidderId == null) {
            val updatedPlayers =
                addAnimalToPlayer(
                    state.players,
                    auctionState.auctioneerId,
                    auctionState.auctionCard,
                )

            val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.AUCTION_RESULT_MS

            state =
                state.copy(
                    phase = GamePhase.AUCTION_RESULT,
                    timerEnd = calculatedTimeout,
                    players = updatedPlayers,
                    auctionState =
                        auctionState.copy(
                            timerEndTime = null,
                            buyerId = auctionState.auctioneerId,
                        ),
                )

            return state
        }

        // If a bid exists, proceed to the auctioneer decision phase
        val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.AUCTIONEER_DECISION_MS

        state =
            state.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                timerEnd = calculatedTimeout,
                auctionState = auctionState.copy(timerEndTime = null),
            )

        return state
    }

    /**
     * Sells the face-up card to the highest bidder.
     */
    private fun makeDefaultAuctioneerDecision(): GameState {
        val auctionState =
            checkNotNull(state.auctionState) {
                "Missing auction state in resolution phase"
            }

        return resolveAuction(actorId = auctionState.auctioneerId, auctioneerBuysCard = false)
    }

    /**
     * Clears auction information and advances the turn.
     */
    private fun endAuctionSequence(): GameState {
        state = state.copy(auctionState = null)

        state = advanceTurnAndCheckGameEnd()

        return state
    }

    /**
     * Submits an empty money selection.
     */
    private fun makeDefaultTradeOffer(): GameState {
        val tradeState =
            checkNotNull(state.tradeState) {
                "Missing trade state in offer phase"
            }

        return submitTradeMoney(tradeState.initiatorId, emptySet())
    }

    /**
     * Makes an empty counteroffer.
     */
    private fun makeDefaultTradeResponse(): GameState {
        val tradeState =
            checkNotNull(state.tradeState) {
                "Missing trade state in response phase"
            }

        return respondToTrade(tradeState.targetId, emptySet())
    }

    /**
     * Clears trade information and advances the turn.
     */
    private fun endTradeSequence(): GameState {
        state = state.copy(tradeState = null)

        state = advanceTurnAndCheckGameEnd()

        return state
    }

    /**
     * Checks if a player exists in the current game session.
     */
    fun hasPlayer(playerId: String): Boolean = state.players.any { it.id == playerId }

    /**
     * Finds the absolute earliest expiration deadline among all active spies.
     * Returns null if no players are actively spying.
     */
    fun getEarliestSpyExpiration(): Long? = state.activeSpies.minOfOrNull { it.expiresAt }

    /**
     * Checks if there are any active spy actions tracked in the session.
     */
    fun hasActiveSpies(): Boolean = state.activeSpies.isNotEmpty()

    /**
     * Clears out any spy actions that have crossed their expiration deadline.
     * Returns true if the state was mutated, and false otherwise.
     */
    fun clearExpiredSpies(): GameState {
        val now = System.currentTimeMillis()
        val (expired, valid) = state.activeSpies.partition { it.expiresAt <= now }

        // Exit early if no changes are needed
        if (expired.isNotEmpty()) {
            state = state.copy(activeSpies = valid.toSet())
        }

        return state
    }

    /**
     * Shifts the active turn indicator to the next player.
     *
     * Transitions the game to [GamePhase.FINISHED] if all animal quartets
     * are completed and to [GamePhase.PLAYER_CHOICE] otherwise.
     */
    private fun advanceTurnAndCheckGameEnd(): GameState {
        val totalQuartetsFormed =
            state.players.sumOf { player ->
                player.animals.groupBy { it.type }.count { (_, cards) -> cards.size == 4 }
            }

        if (totalQuartetsFormed == AnimalType.entries.size) {
            val ranking = ScoreCalculator.calculateGameRanking(state.players)

            state =
                state.copy(
                    phase = GamePhase.FINISHED,
                    timerEnd = null,
                    lastEvent = null,
                    finalRanking = ranking,
                )

            // NOTE FOR FUTURE: This is where the finalRanking data can be extracted to be
            // saved in a global leaderboard on the database later.
            // The source of truth for the winner and their points is available here in 'ranking'.
        } else {
            val calculatedTimeout = System.currentTimeMillis() + PhaseDurations.PLAYER_CHOICE_MS

            state =
                state.copy(
                    phase = GamePhase.PLAYER_CHOICE,
                    timerEnd = calculatedTimeout,
                    roundNumber = state.roundNumber + 1,
                    currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size,
                    lastEvent = null,
                )
        }

        return state
    }

    /**
     * Compiles and shuffles a new deck using the structural maximum allocation per animal type.
     */
    private fun createInitialDeck(): AnimalDeck =
        AnimalDeck(
            AnimalType.entries
                .flatMap { type ->
                    (1..CARDS_PER_ANIMAL_TYPE).map { copyNumber ->
                        AnimalCard(
                            id = "${type.name.lowercase()}-$copyNumber",
                            type = type,
                        )
                    }
                }.shuffled(),
        )

    /**
     * Instantiates starting money card pools using unique, player-bounded identity tags.
     */
    private fun createInitialMoney(playerId: String): List<MoneyCard> =
        buildList {
            // IDs include the player so money cards remain unique after transfers.
            repeat(INITIAL_ZERO_MONEY_CARDS) { index ->
                add(MoneyCard(id = "$playerId-money-0-${index + 1}", value = 0))
            }
            repeat(INITIAL_TEN_MONEY_CARDS) { index ->
                add(MoneyCard(id = "$playerId-money-10-${index + 1}", value = 10))
            }
            repeat(INITIAL_FIFTY_MONEY_CARDS) { index ->
                add(MoneyCard(id = "$playerId-money-50-${index + 1}", value = 50))
            }
        }

    private fun ensureActorInRoom(actorId: String) {
        if (state.players.none { it.id == actorId }) {
            throw GameException(
                GameErrorReason.UNKNOWN_ACTOR,
            )
        }
    }

    private fun requireActorInRoom(actorId: String): Player =
        state.players.find { it.id == actorId }
            ?: throw GameException(GameErrorReason.UNKNOWN_ACTOR)

    private fun ensureHost(playerId: String) {
        if (state.hostPlayerId != playerId) throw GameException(GameErrorReason.NOT_HOST)
    }

    private fun ensurePhase(requiredPhase: GamePhase) {
        if (state.phase != requiredPhase) throw GameException(GameErrorReason.INVALID_PHASE)
    }

    private fun ensureEnoughPlayers() {
        if (state.players.size < MIN_PLAYERS) {
            throw GameException(GameErrorReason.NOT_ENOUGH_PLAYERS)
        }
    }

    private fun ensureRoomNotFull() {
        if (state.players.size >= MAX_PLAYERS) throw GameException(GameErrorReason.ROOM_FULL)
    }

    private fun ensurePlayerNotInRoom(playerId: String) {
        if (state.players.any { it.id == playerId }) {
            throw GameException(
                GameErrorReason.ALREADY_IN_ROOM,
            )
        }
    }

    private fun ensureActivePlayer(playerId: String) {
        if (state.players[state.currentPlayerIndex].id != playerId) {
            throw GameException(
                GameErrorReason.NOT_YOUR_TURN,
            )
        }
    }

    private fun ensureDeckNotEmpty() {
        if (state.deck.isEmpty()) throw GameException(GameErrorReason.DECK_EMPTY)
    }

    private fun ensureNotAuctioneer(
        auctionState: AuctionState,
        playerId: String,
    ) {
        if (auctionState.auctioneerId == playerId) {
            throw GameException(GameErrorReason.OWN_AUCTION)
        }
    }

    private fun ensureNotExcludedFromAuction(
        auctionState: AuctionState,
        playerId: String,
    ) {
        if (auctionState.excludedPlayerIds.contains(playerId)) {
            throw GameException(GameErrorReason.EXCLUDED_FROM_AUCTION)
        }
    }

    // The auctioneer may only buy back the card when they can actually pay the winning bid.
    private fun ensureHasEnoughMoney(
        player: Player,
        amount: Int,
    ) {
        if (player.totalMoney() < amount) {
            throw GameException(
                GameErrorReason.NOT_ENOUGH_MONEY,
            )
        }
    }

    private fun ensureBidNotTooLow(
        auctionState: AuctionState,
        amount: Int,
    ) {
        if (auctionState.highestBid >=
            amount
        ) {
            throw GameException(GameErrorReason.BID_TOO_LOW)
        }
    }

    private fun ensureBidNotTooHigh(amount: Int) {
        val totalOwnedMoney = state.players.sumOf { player -> player.totalMoney() }
        if (amount > totalOwnedMoney) {
            throw GameException(GameErrorReason.BID_TOO_HIGH)
        }
    }

    private fun ensureAuctioneer(
        auctionState: AuctionState,
        playerId: String,
    ) {
        if (auctionState.auctioneerId != playerId) {
            throw GameException(GameErrorReason.NOT_AUCTIONEER)
        }
    }

    private fun requireValidTarget(playerId: String): Player =
        state.players.find { it.id == playerId }
            ?: throw GameException(GameErrorReason.UNKNOWN_TARGET)

    private fun ensureNotTargetingSelf(
        actorId: String,
        tradeTargetId: String,
    ) {
        if (actorId == tradeTargetId) throw GameException(GameErrorReason.TARGETING_SELF)
    }

    private fun ensureTradeInitiator(
        tradeState: TradeState,
        playerId: String,
    ) {
        if (tradeState.initiatorId != playerId) {
            throw GameException(GameErrorReason.NOT_TRADE_INITIATOR)
        }
    }

    private fun ensureTradeTarget(
        tradeState: TradeState,
        playerId: String,
    ) {
        if (tradeState.targetId != playerId) {
            throw GameException(GameErrorReason.NOT_TRADE_TARGET)
        }
    }

    private fun requireTradeInitiatorHasAnimalType(
        initiator: Player,
        animalType: AnimalType,
    ): Set<AnimalCard> {
        val animalCards = initiator.animals.filter { it.type == animalType }
        if (animalCards.isEmpty()) {
            throw GameException(GameErrorReason.INITIATOR_MISSING_ANIMAL)
        }
        return animalCards.toSet()
    }

    private fun requireTradeTargetHasAnimalType(
        target: Player,
        animalType: AnimalType,
    ): Set<AnimalCard> {
        val animalCards = target.animals.filter { it.type == animalType }
        if (animalCards.isEmpty()) {
            throw GameException(GameErrorReason.TARGET_MISSING_ANIMAL)
        }
        return animalCards.toSet()
    }

    private fun requireOwnsMoneyCards(
        player: Player,
        moneyCardIds: Set<String>,
    ): Set<MoneyCard> {
        val moneyCards = player.moneyCards.filter { it.id in moneyCardIds }
        if (moneyCards.size < moneyCardIds.size) {
            throw GameException(GameErrorReason.NOT_OWNED_MONEY_CARDS)
        }
        return moneyCards.toSet()
    }

    private fun ensureSpyNotActivePlayer(playerId: String) {
        if (state.players[state.currentPlayerIndex].id == playerId) {
            throw GameException(GameErrorReason.ACTIVE_PLAYER_CANNOT_SPY)
        }
    }

    private fun ensureNotSpiedThisTurn(playerId: String) {
        if (state.spiedThisTurn.contains(playerId)) {
            throw GameException(GameErrorReason.ALREADY_SPIED_THIS_TURN)
        }
    }

    private fun ensureHasMoneyCardsForSpying(player: Player) {
        if (player.moneyCards.isEmpty()) {
            throw GameException(GameErrorReason.CANNOT_SPY_WITHOUT_MONEY)
        }
    }

    private fun ensureNotSpying(playerId: String) {
        if (state.activeSpies.any { it.spyId == playerId }) {
            throw GameException(GameErrorReason.CANNOT_CATCH_WHILE_SPYING)
        }
    }

    private fun requireValidSpies(
        targetId: String,
        currentTime: Long,
    ): List<SpyAction> {
        val spies =
            state.activeSpies
                .filter { it.targetId == targetId && it.expiresAt >= currentTime }
        if (spies.isEmpty()) {
            throw GameException(GameErrorReason.NOT_SPIED_UPON)
        }
        return spies
    }

    /**
     * Appends an animal card to the given player's collection.
     */
    private fun addAnimalToPlayer(
        players: List<Player>,
        receiverId: String,
        animalCard: AnimalCard,
    ): List<Player> {
        // GameState is immutable, so create an updated player list with the card added to the winner.
        return players.map { player ->
            if (player.id == receiverId) {
                player.copy(animals = player.animals + animalCard)
            } else {
                player
            }
        }
    }

    /**
     * Calculates the mathematically optimal combination of money cards to satisfy a transaction.
     */
    private fun selectMoneyCardsForPayment(
        player: Player,
        amount: Int,
    ): Set<String> {
        check(player.totalMoney() >= amount) {
            "Player ${player.id} total money (${player.totalMoney()}) is less than required payment $amount"
        }

        val bestBySum = mutableMapOf(0 to emptyList<MoneyCard>())
        val sortedMoneyCards =
            player.moneyCards
                .filter { moneyCard -> moneyCard.value > 0 }
                .sortedWith(
                    compareBy<MoneyCard> { moneyCard ->
                        moneyCard.value
                    }.thenBy { moneyCard ->
                        moneyCard.id
                    },
                )

        sortedMoneyCards.forEach { moneyCard ->
            val newOptions =
                bestBySum.mapNotNull { (sum, cards) ->
                    val newSum = sum + moneyCard.value
                    if (bestBySum.containsKey(newSum)) {
                        null
                    } else {
                        newSum to cards + moneyCard
                    }
                }
            bestBySum.putAll(newOptions)
        }

        // Kuhhandel gives no change, so choose the smallest overpayment deterministically.
        val optimalEntry =
            checkNotNull(
                bestBySum
                    .filterKeys { sum -> sum >= amount }
                    .minWithOrNull(
                        compareBy<Map.Entry<Int, List<MoneyCard>>> { (sum, _) -> sum }
                            .thenBy { (_, cards) -> cards.size },
                    ),
            ) { "Failed to find an optimal overpayment" }

        return optimalEntry.value.mapTo(mutableSetOf()) { moneyCard -> moneyCard.id }
    }

    /**
     * Transfers designated money cards between two players.
     */
    private fun transferMoneyCards(
        players: List<Player>,
        from: Player,
        to: Player,
        moneyCardIds: Set<String>,
    ): List<Player> {
        if (moneyCardIds.isEmpty()) {
            return players
        }

        val moneyCardsToTransfer =
            moneyCardIds.map { moneyCardId ->
                checkNotNull(
                    from.moneyCards.find {
                        it.id == moneyCardId
                    },
                ) { "Player ${from.id} does not own money card $moneyCardId" }
            }

        return players.map { player ->
            when (player.id) {
                from.id ->
                    player.copy(
                        moneyCards = player.moneyCards.filterNot { it.id in moneyCardIds },
                    )

                to.id -> player.copy(moneyCards = player.moneyCards + moneyCardsToTransfer)
                else -> player
            }
        }
    }

    companion object {
        const val MIN_PLAYERS = 3
        const val MAX_PLAYERS = 5

        const val CARDS_PER_ANIMAL_TYPE = 4
        const val INITIAL_ZERO_MONEY_CARDS = 2
        const val INITIAL_TEN_MONEY_CARDS = 4
        const val INITIAL_FIFTY_MONEY_CARDS = 1

        const val SPYING_CARDS_REVEALED = 4

        // Used for testing
        fun fromState(
            gameId: String,
            state: GameState,
        ): GameSession {
            val session =
                GameSession(
                    gameId,
                    "",
                    "",
                )
            session.state = state
            return session
        }
    }
}
