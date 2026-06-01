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
import at.aau.kuhhandel.shared.model.PlayerState
import at.aau.kuhhandel.shared.model.TradeState

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
     * Starts a game with a simple initial deck.
     */
    fun startGame(actorId: String): GameState {
        ensureActorInRoom(actorId)
        ensureHost(actorId)
        ensurePhase(GamePhase.NOT_STARTED)
        ensureEnoughPlayers()

        state =
            state.copy(
                phase = GamePhase.PLAYER_CHOICE,
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
     * Adds a player to the game session.
     */
    fun addPlayer(
        playerId: String,
        playerName: String,
    ): GameState {
        ensurePlayerNotInRoom(playerId)
        ensurePhase(GamePhase.NOT_STARTED)
        ensureRoomNotFull()

        val player = PlayerState(playerId, playerName)

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
     * Initiates an auction by drawing the top card from the animal deck.
     */
    fun chooseAuction(actorId: String): GameState {
        ensureActorInRoom(actorId)
        ensurePhase(GamePhase.PLAYER_CHOICE)
        ensureActivePlayer(actorId)
        ensureDeckNotEmpty()

        val (auctionCard, updatedDeck) = state.deck.drawTopCard()
        val card = auctionCard!!

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

        state =
            state.copy(
                phase = GamePhase.AUCTION_BIDDING,
                deck = updatedDeck,
                currentFaceUpCard = card,
                players = updatedPlayers,
                lastEvent = event,
                auctionState =
                    AuctionState(
                        auctionCard = card,
                        auctioneerId = actorId,
                        highestBid = 0,
                        highestBidderId = null,
                        timerEndTime = System.currentTimeMillis() + 5000,
                    ),
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
        requireActorInRoom(actorId)
        ensurePhase(GamePhase.AUCTION_BIDDING)
        ensureNotAuctioneer(actorId)
        ensureNotExcluded(actorId)
        ensureBidNotTooLow(amount)

        state =
            state.copy(
                lastEvent = null,
                auctionState =
                    state.auctionState!!.copy(
                        highestBid = amount,
                        highestBidderId = actorId,
                        timerEndTime = System.currentTimeMillis() + 5000,
                    ),
            )

        return state
    }

    /**
     * Concludes the auction once the bidding timeout ends.
     */
    fun closeAuctionAfterTimeout(): GameState {
        val auctionState = state.auctionState!!

        // If no one placed a bid, the auctioneer gets the card for free.
        // We still transition to AUCTION_RESOLUTION so players can see the result.
        if (auctionState.highestBidderId == null) {
            state =
                state.copy(
                    phase = GamePhase.AUCTION_RESOLUTION,
                    players =
                        addAnimalToPlayer(
                            state.players,
                            auctionState.auctioneerId,
                            auctionState.auctionCard,
                        ),
                    auctionState = auctionState.copy(timerEndTime = null),
                )

            return state
        }

        state =
            state.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                auctionState = auctionState.copy(timerEndTime = null),
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
        ensurePhase(GamePhase.AUCTION_RESOLUTION)
        ensureAuctioneer(actorId)

        val auctionState = state.auctionState!!

        // Handle the 0-bid case (card was already given in closeAuctionAfterTimeout)
        if (auctionState.highestBidderId == null) {
            state =
                state.copy(
                    auctionState = null,
                    currentFaceUpCard = null,
                )
            state = advanceTurnAndCheckGameEnd()

            return state
        }

        val highestBidderId = auctionState.highestBidderId!!
        val highestBidder = state.players.find { it.id == highestBidderId }!!

        // Determine the card receiver and seller
        val receiver: PlayerState
        val seller: PlayerState

        if (auctioneerBuysCard) {
            ensureHasEnoughMoney(actor, auctionState.highestBid)
            receiver = actor
            seller = highestBidder
        } else {
            // Bluff Check: If the winner can't pay the auctioneer, they bluffed.
            if (highestBidder.totalMoney() < auctionState.highestBid) {
                state =
                    state.copy(
                        phase = GamePhase.AUCTION_BIDDING,
                        auctionState =
                            auctionState.copy(
                                highestBid = 0,
                                highestBidderId = null,
                                timerEndTime = System.currentTimeMillis() + 5000,
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

        // Process the payment
        val paymentCardIds = selectMoneyCardsForPayment(receiver, auctionState.highestBid)
        val updatedPlayers =
            transferMoneyCards(state.players, receiver, seller, paymentCardIds)

        // Add the animal card
        state =
            state.copy(
                players = addAnimalToPlayer(updatedPlayers, receiver.id, auctionState.auctionCard),
                auctionState = null,
                currentFaceUpCard = null,
            )
        state = advanceTurnAndCheckGameEnd()

        return state
    }

    /**
     * Initiates a trade against an opponent for a specific animal type.
     */
    fun chooseTrade(
        actorId: String,
        targetId: String,
        animalType: AnimalType,
        offeredMoneyCardIds: Set<String>,
    ): GameState {
        val initiator = requireActorInRoom(actorId)
        ensurePhase(GamePhase.PLAYER_CHOICE)
        ensureActivePlayer(actorId)
        val target = requireValidTradeTarget(targetId)
        ensureNotTargetingSelf(actorId, targetId)
        ensureTradeInitiatorHasAnimalType(initiator, animalType)
        ensureTradeTargetHasAnimalType(target, animalType)
        ensureOfferNotEmpty(offeredMoneyCardIds)
        ensureOwnsMoneyCards(initiator, offeredMoneyCardIds)

        state =
            state.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState =
                    TradeState(
                        initiatorId = initiator.id,
                        targetId = target.id,
                        requestedAnimalType = animalType,
                        offeredMoneyCardIds = offeredMoneyCardIds,
                        counterOfferedMoneyCardIds = emptySet(),
                    ),
            )

        return state
    }

    /**
     * Processes the defender's trade reaction, with empty
     * [counterOfferedMoneyCardIds] representing blind acceptance.
     */
    fun respondToTrade(
        actorId: String,
        counterOfferedMoneyCardIds: Set<String>,
    ): GameState {
        val target = requireActorInRoom(actorId)
        ensurePhase(GamePhase.TRADE_RESPONSE)
        ensureTradeTarget(actorId)

        val tradeState = state.tradeState!!
        val initiator = state.players.find { it.id == tradeState.initiatorId }!!
        var updatedPlayers = state.players

        val offeredMoneyCards =
            initiator.moneyCards.filter { it.id in tradeState.offeredMoneyCardIds }
        var counterOfferedMoneyCards = emptyList<MoneyCard>()

        val winner: PlayerState
        val loser: PlayerState

        if (counterOfferedMoneyCardIds.isEmpty()) {
            // The trade target accepts the trade blindly
            winner = initiator
            loser = target

            // Process the payment
            updatedPlayers =
                transferMoneyCards(updatedPlayers, winner, loser, tradeState.offeredMoneyCardIds)
        } else {
            // The trade target makes a counteroffer
            counterOfferedMoneyCards = requireOwnsMoneyCards(target, counterOfferedMoneyCardIds)

            val initiatorTotal = offeredMoneyCards.sumOf { it.value }
            val targetTotal = counterOfferedMoneyCards.sumOf { it.value }

            if (initiatorTotal >= targetTotal) {
                winner = initiator
                loser = target
            } else {
                winner = target
                loser = initiator
            }

            // Process the payment
            updatedPlayers =
                transferMoneyCards(
                    updatedPlayers,
                    initiator,
                    target,
                    tradeState.offeredMoneyCardIds,
                )
            updatedPlayers =
                transferMoneyCards(
                    updatedPlayers,
                    target,
                    initiator,
                    counterOfferedMoneyCardIds,
                )
        }

        // Move cards of the requested animal type
        state =
            state.copy(
                phase = GamePhase.TRADE_REVEAL,
                players =
                    moveAnimalType(
                        updatedPlayers,
                        loser,
                        winner,
                        tradeState.requestedAnimalType,
                    ),
                tradeState =
                    tradeState.copy(
                        offeredMoney = offeredMoneyCards.sumOf { it.value },
                        counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
                        counterOfferedMoney = counterOfferedMoneyCards.sumOf { it.value },
                    ),
            )

        return state
    }

    /**
     * Clears the trade session information and wraps up the visibility sequence.
     */
    fun endTradeReveal(): GameState {
        check(
            state.phase == GamePhase.TRADE_REVEAL,
        ) { "Expected a trade reveal but the current phase is ${state.phase}" }

        state = state.copy(tradeState = null)
        state = advanceTurnAndCheckGameEnd()

        return state
    }

    /**
     * Checks if a player exists in the current game session.
     */
    fun hasPlayer(playerId: String): Boolean = state.players.any { it.id == playerId }

    /**
     * Shifts the active turn indicator to the next player.
     * Moves phase to FINISHED if all animal quartets are completed.
     */
    private fun advanceTurnAndCheckGameEnd(): GameState {
        val totalQuartetsFormed =
            state.players.sumOf { player ->
                player.animals.groupBy { it.type }.count { (_, cards) -> cards.size == 4 }
            }

        if (totalQuartetsFormed == AnimalType.entries.size) {
            state = state.copy(phase = GamePhase.FINISHED, lastEvent = null)

            // Add score calculation and storing
        } else {
            state =
                state.copy(
                    phase = GamePhase.PLAYER_CHOICE,
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

    private fun requireActorInRoom(actorId: String): PlayerState =
        state.players.find { it.id == actorId }
            ?: throw GameException(GameErrorReason.UNKNOWN_ACTOR)

    private fun ensureHost(playerId: String) {
        if (state.hostPlayerId != playerId) throw GameException(GameErrorReason.NOT_HOST)
    }

    private fun ensurePhase(requiredPhase: GamePhase) {
        if (state.phase != requiredPhase) throw GameException(GameErrorReason.INVALID_PHASE)
    }

    private fun ensureEnoughPlayers() {
        if (state.players.size < 3) throw GameException(GameErrorReason.NOT_ENOUGH_PLAYERS)
    }

    private fun ensureRoomNotFull() {
        if (state.players.size >= 5) throw GameException(GameErrorReason.ROOM_FULL)
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

    private fun ensureNotAuctioneer(playerId: String) {
        if (state.auctionState!!.auctioneerId ==
            playerId
        ) {
            throw GameException(GameErrorReason.OWN_AUCTION)
        }
    }

    private fun ensureNotExcluded(playerId: String) {
        if (state.auctionState?.excludedPlayerIds?.contains(playerId) == true) {
            throw GameException(GameErrorReason.PLAYER_EXCLUDED_FROM_AUCTION)
        }
    }

    // The auctioneer may only buy back the card when they can actually pay the winning bid.
    private fun ensureHasEnoughMoney(
        player: PlayerState,
        amount: Int,
    ) {
        if (player.totalMoney() < amount) {
            throw GameException(
                GameErrorReason.NOT_ENOUGH_MONEY,
            )
        }
    }

    private fun ensureBidNotTooLow(amount: Int) {
        if (state.auctionState!!.highestBid >=
            amount
        ) {
            throw GameException(GameErrorReason.BID_TOO_LOW)
        }
    }

    private fun ensureAuctioneer(playerId: String) {
        if (state.auctionState!!.auctioneerId !=
            playerId
        ) {
            throw GameException(GameErrorReason.NOT_AUCTIONEER)
        }
    }

    private fun requireValidTradeTarget(playerId: String): PlayerState =
        state.players.find { it.id == playerId }
            ?: throw GameException(GameErrorReason.UNKNOWN_TRADE_TARGET)

    private fun ensureNotTargetingSelf(
        actorId: String,
        tradeTargetId: String,
    ) {
        if (actorId == tradeTargetId) throw GameException(GameErrorReason.TARGETING_SELF)
    }

    private fun ensureTradeInitiatorHasAnimalType(
        initiator: PlayerState,
        animalType: AnimalType,
    ) {
        if (initiator.animals.none {
                it.type == animalType
            }
        ) {
            throw GameException(GameErrorReason.INITIATOR_MISSING_ANIMAL)
        }
    }

    private fun ensureTradeTargetHasAnimalType(
        target: PlayerState,
        animalType: AnimalType,
    ) {
        if (target.animals.none {
                it.type == animalType
            }
        ) {
            throw GameException(GameErrorReason.TARGET_MISSING_ANIMAL)
        }
    }

    private fun ensureTradeTarget(playerId: String) {
        if (state.tradeState!!.targetId !=
            playerId
        ) {
            throw GameException(GameErrorReason.NOT_TRADE_TARGET)
        }
    }

    private fun ensureOfferNotEmpty(offeredMoneyCardIds: Set<String>) {
        if (offeredMoneyCardIds.isEmpty()) throw GameException(GameErrorReason.OFFER_EMPTY)
    }

    private fun ensureOwnsMoneyCards(
        player: PlayerState,
        moneyCardIds: Set<String>,
    ) {
        val moneyCards = player.moneyCards.filter { it.id in moneyCardIds }
        if (moneyCards.size <
            moneyCardIds.size
        ) {
            throw GameException(GameErrorReason.NOT_OWNED_MONEY_CARDS)
        }
    }

    private fun requireOwnsMoneyCards(
        player: PlayerState,
        moneyCardIds: Set<String>,
    ): List<MoneyCard> {
        val moneyCards = player.moneyCards.filter { it.id in moneyCardIds }
        if (moneyCards.size <
            moneyCardIds.size
        ) {
            throw GameException(GameErrorReason.NOT_OWNED_MONEY_CARDS)
        }
        return moneyCards
    }

    /**
     * Appends an animal card to the given player's collection.
     */
    private fun addAnimalToPlayer(
        players: List<PlayerState>,
        receiverId: String,
        animalCard: AnimalCard,
    ): List<PlayerState> {
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
        player: PlayerState,
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
     * Moves matching animal cards between two participants following a trade evaluation.
     * Determines whether to move one or two cards based on the counts owned by the players.
     */
    private fun moveAnimalType(
        players: List<PlayerState>,
        from: PlayerState,
        to: PlayerState,
        animalType: AnimalType,
    ): List<PlayerState> {
        val fromMatchingCards = from.animals.filter { it.type == animalType }
        val fromCount = fromMatchingCards.size
        val toCount = to.animals.count { it.type == animalType }

        check(fromCount >= 1) { "Player ${from.id} has $fromCount cards of type $animalType" }
        check(toCount >= 1) { "Player ${to.id} has $toCount cards of type $animalType" }

        val animalCardsToMoveCount = if (fromCount >= 2 && toCount >= 2) 2 else 1
        val animalCardsToMove = fromMatchingCards.take(animalCardsToMoveCount)

        return players.map { player ->
            when (player.id) {
                from.id -> {
                    val nonMatchingCards = player.animals.filterNot { it.type == animalType }
                    val remainingMatchingCards = fromMatchingCards.drop(animalCardsToMoveCount)

                    player.copy(
                        animals = nonMatchingCards + remainingMatchingCards,
                    )
                }

                to.id -> player.copy(animals = player.animals + animalCardsToMove)
                else -> player
            }
        }
    }

    /**
     * Transfers designated money cards between two players.
     */
    private fun transferMoneyCards(
        players: List<PlayerState>,
        from: PlayerState,
        to: PlayerState,
        moneyCardIds: Set<String>,
    ): List<PlayerState> {
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
        const val CARDS_PER_ANIMAL_TYPE = 4
        const val INITIAL_ZERO_MONEY_CARDS = 2
        const val INITIAL_TEN_MONEY_CARDS = 4
        const val INITIAL_FIFTY_MONEY_CARDS = 1

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
