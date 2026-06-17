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
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionTest {
    // Baseline empty state to use across tests via .copy()
    private val baselineState =
        GameState(
            phase = GamePhase.NOT_STARTED,
            players =
                listOf(
                    Player(id = "player-1", name = "Player 1"),
                    Player(id = "player-2", name = "Player 2"),
                    Player(id = "player-3", name = "Player 3"),
                ),
            hostPlayerId = "player-1",
        )

    @Test
    fun `GameSession constructor initializes state correctly with the host player`() {
        // Act: Instantiate using the real primary constructor, not the fromState helper
        val session =
            GameSession(
                gameId = "game-1",
                hostPlayerId = "player-1",
                hostPlayerName = "Player 1",
            )

        // Assert: Verify the outer properties
        assertEquals("game-1", session.gameId)

        // Assert: Verify the nested baseline state parameters
        val initialState = session.state
        assertEquals(GamePhase.NOT_STARTED, initialState.phase)
        assertEquals(0, initialState.roundNumber)
        assertEquals(-1, initialState.currentPlayerIndex)
        assertNull(initialState.currentFaceUpCard)
        assertNull(initialState.auctionState)
        assertNull(initialState.tradeState)

        // Assert: Verify the host was automatically added as the first player
        assertEquals("player-1", initialState.hostPlayerId)
        assertEquals(1, initialState.players.size)

        val hostPlayer = initialState.players.first()
        assertEquals("player-1", hostPlayer.id)
        assertEquals("Player 1", hostPlayer.name)
        assertTrue(hostPlayer.animals.isEmpty())
        assertTrue(hostPlayer.moneyCards.isEmpty())
    }

    @Test
    fun `addPlayer adds player to room and does not change host`() {
        val session = GameSession.fromState("game-1", baselineState)

        val updatedState = session.addPlayer("player-4", "Player 4")

        assertEquals(4, updatedState.players.size)
        assertTrue(updatedState.players.any { it.id == "player-4" && it.name == "Player 4" })
        assertEquals("player-1", updatedState.hostPlayerId)
    }

    @Test
    fun `addPlayer adds player to empty room and sets initial host`() {
        val emptyState =
            GameState(
                phase = GamePhase.NOT_STARTED,
                players = emptyList(),
                hostPlayerId = null,
            )
        val session = GameSession.fromState("game-1", emptyState)

        val updatedState = session.addPlayer("player-1", "Player 1")

        assertEquals(1, updatedState.players.size)
        assertEquals("player-1", updatedState.hostPlayerId)
    }

    @Test
    fun `addPlayer fails if player is already in room`() {
        assertActionFailsWithReason(baselineState, GameErrorReason.ALREADY_IN_ROOM) {
            it.addPlayer("player-1", "Player 4")
        }
    }

    @Test
    fun `addPlayer fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.addPlayer(
                "player-4",
                "Player 4",
            )
        }
    }

    @Test
    fun `addPlayer fails if room is full`() {
        assertActionFailsWithReason(
            baselineState.copy(
                players =
                    baselineState.players +
                        Player(id = "player-4", "Player-4") +
                        Player(id = "player-5", "Player 5"),
            ),
            GameErrorReason.ROOM_FULL,
        ) {
            it.addPlayer("player-6", "Player 6")
        }
    }

    @Test
    fun `removePlayer removes player from room and does not change host`() {
        val session = GameSession.fromState("game-1", baselineState)

        val updatedState = session.removePlayer("player-2")

        assertEquals(2, updatedState.players.size)
        assertTrue(updatedState.players.none { it.id == "player-2" })
        assertEquals("player-1", updatedState.hostPlayerId)
    }

    @Test
    fun `removePlayer removes host from room and migrates host to next player`() {
        val session = GameSession.fromState("game-1", baselineState)

        val updatedState = session.removePlayer("player-1")

        assertEquals(2, updatedState.players.size)
        assertTrue(updatedState.players.none { it.id == "player-1" })
        assertNotNull(updatedState.hostPlayerId)
    }

    @Test
    fun `removePlayer removes last player from room and sets host to null`() {
        val singlePlayerState =
            GameState(
                phase = GamePhase.NOT_STARTED,
                players = listOf(Player(id = "player-1", name = "player-1")),
                hostPlayerId = "player-1",
            )
        val session = GameSession.fromState("game-1", singlePlayerState)

        val updatedState = session.removePlayer("player-1")

        assertTrue(updatedState.players.isEmpty())
        assertNull(updatedState.hostPlayerId)
    }

    @Test
    fun `removePlayer fails if player is not in room`() {
        assertFailsWithMissingActor(baselineState) { it.removePlayer("nonexistent player") }
    }

    @Test
    fun `removePlayer fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.removePlayer("player-2")
        }
    }

    @Test
    fun `disconnectPlayer removes player from room if phase is NOT_STARTED`() {
        val session = GameSession.fromState("game-1", baselineState)

        val updatedState = session.disconnectPlayer("player-2")

        assertEquals(2, updatedState.players.size)
        assertTrue(updatedState.players.none { it.id == "player-2" })
        assertEquals("player-1", updatedState.hostPlayerId)
    }

    @Test
    fun `disconnectPlayer marks player as disconnected if game is running`() {
        val activeGameState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        val session = GameSession.fromState("game-1", activeGameState)

        val updatedState = session.disconnectPlayer("player-2")

        assertEquals(3, updatedState.players.size)
        val targetPlayer = updatedState.players.first { it.id == "player-2" }
        assertFalse(targetPlayer.isConnected)
    }

    @Test
    fun `disconnectPlayer fails if player is not in room`() {
        assertActionFailsWithReason(
            baselineState,
            GameErrorReason.UNKNOWN_PLAYER,
        ) { it.disconnectPlayer("nonexistent player") }
    }

    @Test
    fun `disconnectPlayer fails if player is already marked as disconnected`() {
        val disconnectedInGameState =
            baselineState
                .copy(phase = GamePhase.PLAYER_CHOICE)
                .updatePlayer("player-2") { it.copy(isConnected = false) }

        assertActionFailsWithReason(disconnectedInGameState, GameErrorReason.ALREADY_DISCONNECTED) {
            it.disconnectPlayer("player-2")
        }
    }

    @Test
    fun `reconnectPlayer marks player as connected`() {
        val offlineInGameState =
            baselineState
                .copy(phase = GamePhase.PLAYER_CHOICE)
                .updatePlayer("player-2") { it.copy(isConnected = false) }
        val session = GameSession.fromState("game-1", offlineInGameState)

        val updatedState = session.reconnectPlayer("player-2")

        val targetPlayer = updatedState.players.first { it.id == "player-2" }
        assertTrue(targetPlayer.isConnected)
    }

    @Test
    fun `reconnectPlayer fails if player is already marked as connected`() {
        val connectedInGameState =
            baselineState
                .copy(phase = GamePhase.PLAYER_CHOICE)
                .updatePlayer("player-2") { it.copy(isConnected = true) }

        assertActionFailsWithReason(connectedInGameState, GameErrorReason.ALREADY_CONNECTED) {
            it.reconnectPlayer("player-2")
        }
    }

    @Test
    fun `reconnectPlayer fails if player is not in room`() {
        assertActionFailsWithReason(
            baselineState,
            GameErrorReason.UNKNOWN_PLAYER,
        ) { it.reconnectPlayer("nonexistent player") }
    }

    @Test
    fun `startGame shuffles players, distributes initial money, and transitions phase`() {
        val session = GameSession.fromState("game-1", baselineState)

        val updatedState = session.startGame("player-1")

        // Phase, Round, and Starting Turn assertions
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.roundNumber)
        assertEquals(0, updatedState.currentPlayerIndex)

        // Confirm the timeout has been set
        assertValidTimeout(expectedDuration = PhaseDurations.PLAYER_CHOICE_MS, state = updatedState)

        // Deck assertion (CARDS_PER_ANIMAL_TYPE * number of animal types)
        assertEquals(FULL_DECK_SIZE, updatedState.deck.cards.size)

        // Player asset distribution assertions
        assertEquals(3, updatedState.players.size)
        updatedState.players.forEach { player ->
            assertTrue(player.animals.isEmpty())
            assertEquals(STARTING_MONEY_VALUES.size, player.moneyCards.size)
            assertEquals(STARTING_MONEY_VALUES.sum(), player.totalMoney())
        }
    }

    @Test
    fun `startGame fails if actor is not in room`() {
        assertFailsWithMissingActor(baselineState) { it.startGame("nonexistent player") }
    }

    @Test
    fun `startGame fails if actor is not the host`() {
        assertActionFailsWithReason(
            baselineState,
            GameErrorReason.NOT_HOST,
        ) { it.startGame("player-2") }
    }

    @Test
    fun `startGame fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.startGame("player-1")
        }
    }

    @Test
    fun `startGame fails if there are not enough players`() {
        val lowPlayerState =
            baselineState.copy(
                players =
                    listOf(
                        Player(id = "player-1", name = "Player 1"),
                        Player(id = "player-2", name = "Player 2"),
                    ),
            )
        assertActionFailsWithReason(lowPlayerState, GameErrorReason.NOT_ENOUGH_PLAYERS) {
            it.startGame("player-1")
        }
    }

    @Test
    fun `chooseAuction draws top card and transitions to bidding phase`() {
        // Setup: Ensure the deck has a known card on top to trace
        val expectedCard = AnimalCard("cow-1", AnimalType.COW)
        val testDeck = AnimalDeck(listOf(expectedCard, AnimalCard("pig-1", AnimalType.PIG)))
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                deck = testDeck,
                currentPlayerIndex = 0, // Player 1 is active
            )
        val session = GameSession.fromState("game-1", playableState)

        val updatedState = session.chooseAuction("player-1")

        // Phase & Card drawing assertions
        assertEquals(GamePhase.AUCTION_BIDDING, updatedState.phase)
        assertEquals(1, updatedState.deck.cards.size) // One card remaining

        // Confirm the timeout has been set
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_BIDDING_MS,
            state = updatedState,
        )

        // Auction state initialization assertions
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(expectedCard, auction.auctionCard)
        assertEquals("player-1", auction.auctioneerId)
        assertEquals(0, auction.highestBid)
        assertNull(auction.highestBidderId)
    }

    @Test
    fun `chooseAuction handles donkey bonus`() {
        // Setup: Donkey is on top of the deck. All players get money.
        val donkey = AnimalCard("donkey-1", AnimalType.DONKEY)
        val testDeck =
            AnimalDeck(
                listOf(donkey) + List(3) { AnimalCard("donkey-${it + 2}", AnimalType.DONKEY) },
            )
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                deck = testDeck,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", playableState)

        // Act: Draw the donkey card
        val updatedState = session.chooseAuction("player-1")

        // Assert: All players receive the money card bonus
        updatedState.players.forEach { player ->
            assertTrue(player.moneyCards.any { it.value == 50 && it.id.contains("donkey-1") })
        }
        assertNotNull(updatedState.lastEvent)
        assertTrue(updatedState.lastEvent is GameEvent.MoneyBonus)
        assertEquals(50, (updatedState.lastEvent as GameEvent.MoneyBonus).amount)

        // Assert: The unified countdown timer is initialized on the state
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_BIDDING_MS,
            state = updatedState,
        )
    }

    @Test
    fun `chooseAuction fails if actor is not in room`() {
        assertFailsWithMissingActor(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.chooseAuction("nonexistent player")
        }
    }

    @Test
    fun `chooseAuction fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.NOT_STARTED)) {
            it.chooseAuction("player-1")
        }
    }

    @Test
    fun `chooseAuction fails if actor is not the active player`() {
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 1, // It is player-2's turn
            )
        assertActionFailsWithReason(playableState, GameErrorReason.NOT_YOUR_TURN) {
            it.chooseAuction("player-1")
        }
    }

    @Test
    fun `chooseAuction fails if the deck is empty`() {
        val emptyDeckState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                deck = AnimalDeck(emptyList()),
                currentPlayerIndex = 0,
            )
        assertActionFailsWithReason(emptyDeckState, GameErrorReason.DECK_EMPTY) {
            it.chooseAuction("player-1")
        }
    }

    @Test
    fun `placeBid updates highest bid and extends timer`() {
        // Setup: Active auction run by Player 1. Player 2 bids 20.
        val initialAuction =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 10,
                highestBidderId = "player-3",
            )
        // Add the 50 and 10 bills to Player 2
        val stateWithMoney =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-2",
                        moneyValues = listOf(50, 10),
                    ),
            )
        val session = GameSession.fromState("game-1", stateWithMoney)

        val updatedState = session.placeBid("player-2", amount = 20)

        // Verify bidding updates correctly
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(20, auction.highestBid)
        assertEquals("player-2", auction.highestBidderId)

        // Confirm the timeout has been set
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_BIDDING_MS,
            state = updatedState,
        )
    }

    @Test
    fun `placeBid allows actor to bid more than available money as bluff`() {
        val initialAuction =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 0,
                highestBidderId = null,
            )

        val smallBillValue = STARTING_MONEY_VALUES.first { it > 0 }
        val illegalBidAmount = smallBillValue * 2

        // Player setup
        val poorPlayerState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
                players =
                    baselineState.players
                        .withPlayerAssets("player-1", moneyValues = listOf(10, 20))
                        .withPlayerAssets("player-2", moneyValues = listOf(smallBillValue))
                        .withPlayerAssets("player-3", moneyValues = listOf(100)),
            )
        val session = GameSession.fromState("game-1", poorPlayerState)

        val updatedState = session.placeBid("player-2", amount = illegalBidAmount)

        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(illegalBidAmount, auction.highestBid)
        assertEquals("player-2", auction.highestBidderId)
    }

    @Test
    fun `placeBid fails if actor is not in room`() {
        assertFailsWithMissingActor(baselineState.copy(phase = GamePhase.AUCTION_BIDDING)) {
            it.placeBid("nonexistent player", amount = 10)
        }
    }

    @Test
    fun `placeBid fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.placeBid("player-2", amount = 10)
        }
    }

    @Test
    fun `placeBid throws IllegalStateException if auction state is missing`() {
        val brokenState = baselineState.copy(phase = GamePhase.AUCTION_BIDDING)
        val session = GameSession.fromState("game-1", brokenState)

        assertThrows<IllegalStateException> { session.placeBid("player-2", amount = 10) }
    }

    @Test
    fun `placeBid fails if actor is the auctioneer`() {
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState =
                    AuctionState(
                        auctionCard = AnimalCard("cow-1", AnimalType.COW),
                        auctioneerId = "player-1",
                        highestBid = 0,
                        highestBidderId = null,
                    ),
            )
        assertActionFailsWithReason(biddingState, GameErrorReason.OWN_AUCTION) {
            it.placeBid("player-1", amount = 10)
        }
    }

    @Test
    fun `placeBid fails if amount is lower than or equal to current highest bid`() {
        val initialAuction =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 30, // Current highest bid is 30
                highestBidderId = "player-3",
            )
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-2",
                        moneyValues = listOf(50),
                    ),
            )

        assertActionFailsWithReason(biddingState, GameErrorReason.BID_TOO_LOW) {
            it.placeBid("player-2", amount = 25) // 25 <= 30, should fail
        }
    }

    @Test
    fun `placeBid fails if amount is higher than the total owned money`() {
        val initialAuction =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 10,
                highestBidderId = "player-3",
            )
        // Total owned money: 10 + 20 + 50 + 10 + 100 = 190
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
                players =
                    baselineState.players
                        .withPlayerAssets("player-1", moneyValues = listOf(10, 20))
                        .withPlayerAssets("player-2", moneyValues = listOf(50, 10))
                        .withPlayerAssets("player-3", moneyValues = listOf(100)),
            )

        assertActionFailsWithReason(biddingState, GameErrorReason.BID_TOO_HIGH) {
            it.placeBid("player-2", amount = 200) // 200 > 190, should fail
        }
    }

    @Test
    fun `closeAuctionBidding handles the edge case if no one placed a bid`() {
        // Setup: Auctioneer is Player 1, face-up card is a COW, and no bids were placed
        val testCard = AnimalCard("cow-1", AnimalType.COW)
        val initialAuction =
            AuctionState(
                auctionCard = testCard,
                auctioneerId = "player-1",
                highestBid = 0,
                highestBidderId = null, // Edge case: no one placed a bid
            )
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
                currentPlayerIndex = 0, // It was Player 1's turn
            )
        val session = GameSession.fromState("game-1", biddingState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Auctioneer received the animal card for free
        val auctioneer = updatedState.players.find { it.id == "player-1" }!!
        assertTrue(auctioneer.animals.any { it.id == "cow-1" && it.type == AnimalType.COW })

        // Assert: Transitions straight to AUCTION_RESULT
        assertEquals(GamePhase.AUCTION_RESULT, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_RESULT_MS,
            state = updatedState,
        )

        // Assert: Verify that buyerId is assigned correctly to the auctioneer
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals("player-1", auction.buyerId)
    }

    @Test
    fun `closeAuctionBidding functions correctly if a highest bidder exists`() {
        // Setup: Player 2 placed a winning bid of 20
        val testCard = AnimalCard("cow-1", AnimalType.COW)
        val initialAuction =
            AuctionState(
                auctionCard = testCard,
                auctioneerId = "player-1",
                highestBid = 20,
                highestBidderId = "player-2",
            )
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
            )
        val session = GameSession.fromState("game-1", biddingState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Transitions to the AUCTIONEER_DECISION phase
        assertEquals(GamePhase.AUCTIONEER_DECISION, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTIONEER_DECISION_MS,
            state = updatedState,
        )

        // Assert: The auction details remain intact
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(20, auction.highestBid)
        assertEquals("player-2", auction.highestBidderId)
        assertNull(auction.buyerId)

        // Assert: The card has NOT been given away yet
        updatedState.players.forEach { player ->
            assertTrue(player.animals.isEmpty())
        }
    }

    @Test
    fun `closeAuctionBidding throws IllegalStateException if auction state is missing`() {
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = null,
            )
        val session = GameSession.fromState("game-1", biddingState)

        // Assert: Internal structural engine assertion holds
        assertThrows<IllegalStateException> { session.handleTimeoutExpiration() }
    }

    @Test
    fun `resolveAuction functions correctly when auctioneer sells`() {
        // Setup: Player 1 is auctioneer. Player 2 wins with a bid of 20 and has two 10 money cards.
        val targetCard = AnimalCard("cow-1", AnimalType.COW)
        val auctionState =
            AuctionState(
                auctionCard = targetCard,
                auctioneerId = "player-1",
                highestBid = 20,
                highestBidderId = "player-2",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-2",
                        moneyValues = listOf(10, 10),
                    ),
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: Auctioneer decides NOT to buy
        val paymentState = session.resolveAuction("player-1", auctioneerBuysCard = false)

        // Assert: Transitions to the payment phase with buyer/seller recorded, no money moved yet
        assertEquals(GamePhase.AUCTION_PAYMENT, paymentState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_PAYMENT_MS,
            state = paymentState,
        )
        val paymentAuction = paymentState.auctionState
        assertNotNull(paymentAuction)
        assertEquals("player-2", paymentAuction.buyerId)
        assertEquals("player-1", paymentAuction.sellerId)
        assertEquals(20, paymentState.players.find { it.id == "player-2" }!!.totalMoney())

        // Act: Buyer pays with both 10 cards
        val buyerCardIds =
            paymentState.players
                .find { it.id == "player-2" }!!
                .moneyCards
                .map { it.id }
                .toSet()
        val updatedState = session.submitAuctionPayment("player-2", buyerCardIds)

        val seller = updatedState.players.find { it.id == "player-1" }!!
        val buyer = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Buyer gets the animal card, money transfers from buyer to seller
        assertTrue(buyer.animals.contains(targetCard))
        assertTrue(buyer.moneyCards.isEmpty())
        assertEquals(2, seller.moneyCards.size)
        assertEquals(20, seller.totalMoney())

        // Assert: Transitions to auction result phase
        assertEquals(GamePhase.AUCTION_RESULT, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_RESULT_MS,
            state = updatedState,
        )

        // Assert: Buyer identity is logged in auction state
        val finalAuction = updatedState.auctionState
        assertNotNull(finalAuction)
        assertEquals("player-2", finalAuction.buyerId)
    }

    @Test
    fun `resolveAuction functions correctly when auctioneer buys`() {
        // Setup: Player 1 is auctioneer and has a 50 money card. Player 2 bid 20.
        val targetCard = AnimalCard("cow-1", AnimalType.COW)
        val auctionState =
            AuctionState(
                auctionCard = targetCard,
                auctioneerId = "player-1",
                highestBid = 20,
                highestBidderId = "player-2",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-1",
                        moneyValues = listOf(50),
                    ),
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: Auctioneer buys the card
        val paymentState = session.resolveAuction("player-1", auctioneerBuysCard = true)

        // Assert: Transitions to the payment phase with the auctioneer as buyer
        assertEquals(GamePhase.AUCTION_PAYMENT, paymentState.phase)
        val paymentAuction = paymentState.auctionState
        assertNotNull(paymentAuction)
        assertEquals("player-1", paymentAuction.buyerId)
        assertEquals("player-2", paymentAuction.sellerId)

        // Act: Auctioneer pays with their 50 card
        val buyerCardIds =
            paymentState.players
                .find { it.id == "player-1" }!!
                .moneyCards
                .map { it.id }
                .toSet()
        val updatedState = session.submitAuctionPayment("player-1", buyerCardIds)

        val buyer = updatedState.players.find { it.id == "player-1" }!!
        val seller = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Auctioneer gets the card and gives their 50 card away
        assertTrue(buyer.animals.contains(targetCard))
        assertTrue(buyer.moneyCards.isEmpty())
        assertEquals(1, seller.moneyCards.size)
        assertEquals(50, seller.totalMoney())

        // Assert: Transitions to auction result phase
        assertEquals(GamePhase.AUCTION_RESULT, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_RESULT_MS,
            state = updatedState,
        )

        // Assert: Buyer identity is logged in auction state
        val finalAuction = updatedState.auctionState
        assertNotNull(finalAuction)
        assertEquals("player-1", finalAuction.buyerId)
    }

    @Test
    fun `resolveAuction fails if actor is not in room`() {
        assertFailsWithMissingActor(baselineState.copy(phase = GamePhase.AUCTIONEER_DECISION)) {
            it.resolveAuction("nonexistent player", auctioneerBuysCard = false)
        }
    }

    @Test
    fun `resolveAuction fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.resolveAuction("player-1", auctioneerBuysCard = false)
        }
    }

    @Test
    fun `resolveAuction throws IllegalStateException if auction state is missing`() {
        val brokenState = baselineState.copy(phase = GamePhase.AUCTIONEER_DECISION)
        val session = GameSession.fromState("game-1", brokenState)

        assertThrows<IllegalStateException> {
            session.resolveAuction("player-1", auctioneerBuysCard = false)
        }
    }

    @Test
    fun `resolveAuction fails if actor is not the auctioneer`() {
        // Setup: Active auction run by Player 1
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 10,
                highestBidderId = "player-2",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
            )

        // Act & Assert: Player 2 tries to resolve out of role bounds
        assertActionFailsWithReason(resolutionState, GameErrorReason.NOT_AUCTIONEER) {
            it.resolveAuction("player-2", auctioneerBuysCard = false)
        }
    }

    @Test
    fun `resolveAuction throws IllegalStateException if highest bidder is missing`() {
        // Setup: Active decision phase but missing critical tracking node
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 0,
                highestBidderId = null,
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act & Assert: Exception is thrown
        assertThrows<IllegalStateException> {
            session.resolveAuction("player-1", auctioneerBuysCard = true)
        }
    }

    @Test
    fun `resolveAuction fails if auctioneer buys but lacks enough money`() {
        // Setup: Auctioneer only has 10 money, but needs 40 to buy back
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 40,
                highestBidderId = "player-2",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-1",
                        moneyValues = listOf(10),
                    ),
            )

        // Act & Assert: Transaction validation check fails
        assertActionFailsWithReason(resolutionState, GameErrorReason.NOT_ENOUGH_MONEY) {
            it.resolveAuction("player-1", auctioneerBuysCard = true)
        }
    }

    @Test
    fun `resolveAuction restarts auction and excludes bidder when winning bid was a bluff`() {
        // Setup: Player 2 bid 20 but only has 10 total value in hand
        val targetCard = AnimalCard("cow-1", AnimalType.COW)
        val auctionState =
            AuctionState(
                auctionCard = targetCard,
                auctioneerId = "player-1",
                highestBid = 20,
                highestBidderId = "player-2",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-2",
                        moneyValues = listOf(10),
                    ),
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: Auctioneer passes buyback execution
        val updatedState = session.resolveAuction("player-1", auctioneerBuysCard = false)

        // Assert: Game loop rolls back to bidding room with fresh timer tracking
        assertEquals(GamePhase.AUCTION_BIDDING, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_BIDDING_MS,
            state = updatedState,
        )

        // Assert: High bidder is penalized and excluded from subsequent restarts
        val restartedAuction = updatedState.auctionState
        assertNotNull(restartedAuction)
        assertEquals(targetCard, restartedAuction.auctionCard)
        assertEquals(0, restartedAuction.highestBid)
        assertNull(restartedAuction.highestBidderId)
        assertTrue("player-2" in restartedAuction.excludedPlayerIds)

        // Assert: System logs global alert notification event payload
        val event = updatedState.lastEvent
        assertTrue(event is GameEvent.BluffDetected)
        assertEquals("player-2", event.playerId)
    }

    @Test
    fun `chooseTrade creates trade state and transitions phase on happy path`() {
        // Setup: Player 1 initiates a trade with Player 2 for a COW. Both own a COW.
        val cow1 = AnimalCard("c1", AnimalType.COW)
        val cow2 = AnimalCard("c2", AnimalType.COW)
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players =
                    baselineState.players
                        .withPlayerAssets(
                            "player-1",
                            animals = listOf(cow1),
                            moneyValues = listOf(10),
                        ).withPlayerAssets("player-2", animals = listOf(cow2)),
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", playableState)

        // Act: Initiate trade declaration with no money cards attached yet
        val updatedState =
            session.chooseTrade(
                actorId = "player-1",
                targetId = "player-2",
                animalType = AnimalType.COW,
            )

        val initiator = updatedState.players.find { it.id == "player-1" }!!
        val target = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Phase transitions to trade offer and sets the countdown window
        assertEquals(GamePhase.TRADE_OFFER, updatedState.phase)
        assertValidTimeout(expectedDuration = PhaseDurations.TRADE_OFFER_MS, state = updatedState)

        // Assert: At-stake animal cards are extracted out of player inventories
        assertTrue(initiator.animals.isEmpty())
        assertTrue(target.animals.isEmpty())

        // Assert: Trade details pool the at-stake animal items and leave money selections unpopulated
        val trade = updatedState.tradeState
        assertNotNull(trade)
        assertEquals("player-1", trade.initiatorId)
        assertEquals("player-2", trade.targetId)
        assertEquals(AnimalType.COW, trade.requestedAnimalType)
        assertEquals(setOf(cow1, cow2), trade.animalCards)
        assertNull(trade.offeredMoneyCards)
        assertNull(trade.counterOfferedMoneyCards)
    }

    @Test
    fun `chooseTrade extracts multiple cards when both participants own multiple cards`() {
        // Setup: Both players hold multiple matching animals, meaning a 2-card trade is forced
        val p1Cows =
            listOf(AnimalCard("p1-c1", AnimalType.COW), AnimalCard("p1-c2", AnimalType.COW))
        val p2Cows =
            listOf(AnimalCard("p2-c1", AnimalType.COW), AnimalCard("p2-c2", AnimalType.COW))
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players =
                    baselineState.players
                        .withPlayerAssets("player-1", animals = p1Cows)
                        .withPlayerAssets("player-2", animals = p2Cows),
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", playableState)

        // Act: Initiate multi-card challenge
        val updatedState =
            session.chooseTrade(
                actorId = "player-1",
                targetId = "player-2",
                animalType = AnimalType.COW,
            )

        val initiator = updatedState.players.find { it.id == "player-1" }!!
        val target = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Exactly 2 animal cards are extracted from each hand into the pooled collection
        assertTrue(initiator.animals.isEmpty())
        assertTrue(target.animals.isEmpty())

        val trade = updatedState.tradeState
        assertNotNull(trade)
        assertEquals(4, trade.animalCards.size)
        assertEquals((p1Cows + p2Cows).toSet(), trade.animalCards)
    }

    @Test
    fun `chooseTrade fails if actor is not in room`() {
        assertFailsWithMissingActor(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.chooseTrade("nonexistent player", "player-2", AnimalType.COW)
        }
    }

    @Test
    fun `chooseTrade fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.NOT_STARTED)) {
            it.chooseTrade("player-1", "player-2", AnimalType.COW)
        }
    }

    @Test
    fun `chooseTrade fails if actor is not the active player`() {
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 1,
            )
        assertActionFailsWithReason(playableState, GameErrorReason.NOT_YOUR_TURN) {
            it.chooseTrade("player-1", "player-2", AnimalType.COW)
        }
    }

    @Test
    fun `chooseTrade fails if trade target does not exist`() {
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
            )

        assertActionFailsWithReason(playableState, GameErrorReason.UNKNOWN_TARGET) {
            it.chooseTrade("player-1", "fake-target-id", AnimalType.COW)
        }
    }

    @Test
    fun `chooseTrade fails if actor targets themselves`() {
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
            )

        assertActionFailsWithReason(playableState, GameErrorReason.TARGETING_SELF) {
            it.chooseTrade("player-1", "player-1", AnimalType.COW)
        }
    }

    @Test
    fun `chooseTrade fails if initiator does not own the requested animal type`() {
        // Setup: Player 1 has a PIG instead of a COW, breaking initial ownership conditions
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
                players =
                    baselineState.players
                        .withPlayerAssets(
                            "player-1",
                            animals = listOf(AnimalCard("p1", AnimalType.PIG)),
                        ).withPlayerAssets(
                            "player-2",
                            animals = listOf(AnimalCard("c1", AnimalType.COW)),
                        ),
            )

        // Act & Assert: Initiator animal card presence assertion fails
        assertActionFailsWithReason(playableState, GameErrorReason.INITIATOR_MISSING_ANIMAL) {
            it.chooseTrade("player-1", "player-2", AnimalType.COW)
        }
    }

    @Test
    fun `chooseTrade fails if target does not own the requested animal type`() {
        // Setup: Target player hand contains no animal cards to swap back
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
                players =
                    baselineState.players
                        .withPlayerAssets(
                            "player-1",
                            animals = listOf(AnimalCard("c1", AnimalType.COW)),
                        ),
                // Player 2 remains empty by default
            )

        // Act & Assert: Target animal card presence assertion fails
        assertActionFailsWithReason(playableState, GameErrorReason.TARGET_MISSING_ANIMAL) {
            it.chooseTrade("player-1", "player-2", AnimalType.COW) // <-- throws NOT_TRADE_TARGET
        }
    }

    @Test
    fun `submitTradeMoney moves money cards from initiator to trade state`() {
        // Setup: Active trade offer phase. Initiator submits a valid 10 euro card.
        val targetCardId = "player-1-10-1"
        val activeTrade =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                animalCards = setOf(AnimalCard("c1", AnimalType.COW)),
            )
        val initialSetup =
            baselineState.copy(
                phase = GamePhase.TRADE_OFFER,
                tradeState = activeTrade,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-1",
                        moneyValues = listOf(10),
                    ),
            )
        val session = GameSession.fromState("game-1", initialSetup)

        // Act: Submit the money cards
        val updatedState = session.submitTradeMoney("player-1", setOf(targetCardId))

        val initiator = updatedState.players.find { it.id == "player-1" }!!

        // Assert: Phase transitions to response and registers the timeline window
        assertEquals(GamePhase.TRADE_RESPONSE, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.TRADE_RESPONSE_MS,
            state = updatedState,
        )

        // Assert: Money cards are stripped out of the player hand collection
        assertTrue(initiator.moneyCards.isEmpty())

        // Assert: Trade details store the updated selection parameters
        val trade = updatedState.tradeState
        assertNotNull(trade)
        val offeredCards = checkNotNull(trade.offeredMoneyCards)
        assertEquals(1, offeredCards.size)
        assertEquals(10, trade.offeredMoney)
        assertTrue(offeredCards.any { it.id == targetCardId })
    }

    @Test
    fun `submitTradeMoney allows empty submissions`() {
        // Setup: Active trade offer phase. Initiator submits an empty set.
        val activeTrade =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                animalCards = setOf(AnimalCard("c1", AnimalType.COW)),
            )
        val initialSetup =
            baselineState.copy(
                phase = GamePhase.TRADE_OFFER,
                tradeState = activeTrade,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-1",
                        moneyValues = listOf(10),
                    ),
            )
        val session = GameSession.fromState("game-1", initialSetup)

        // Act: Submit zero cards
        val updatedState = session.submitTradeMoney("player-1", emptySet())

        val initiator = updatedState.players.find { it.id == "player-1" }!!

        // Assert: Phase transitions to response and registers the timeline window
        assertEquals(GamePhase.TRADE_RESPONSE, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.TRADE_RESPONSE_MS,
            state = updatedState,
        )

        // Assert: Player hand configuration remains completely untouched
        assertEquals(1, initiator.moneyCards.size)

        // Assert: Trade state updates with an empty, non-null set
        val trade = updatedState.tradeState
        assertNotNull(trade)
        val offeredCards = checkNotNull(trade.offeredMoneyCards)
        assertTrue(offeredCards.isEmpty())
        assertEquals(0, trade.offeredMoney)
    }

    @Test
    fun `submitTradeMoney fails if actor is not in room`() {
        val activeTrade =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
            )
        val initialSetup =
            baselineState.copy(
                phase = GamePhase.TRADE_OFFER,
                tradeState = activeTrade,
            )

        assertFailsWithMissingActor(initialSetup) {
            it.submitTradeMoney("nonexistent player", setOf("some-card-id"))
        }
    }

    @Test
    fun `submitTradeMoney fails if phase is invalid`() {
        val activeTrade =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
            )
        val brokenState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                tradeState = activeTrade,
            )

        assertFailsWithInvalidPhase(brokenState) {
            it.submitTradeMoney("player-1", setOf("some-card-id"))
        }
    }

    @Test
    fun `submitTradeMoney fails if actor is not the trade initiator`() {
        // Setup: Player 2 tries to run initiator submission tasks
        val activeTrade =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
            )
        val initialSetup =
            baselineState.copy(phase = GamePhase.TRADE_OFFER, tradeState = activeTrade)

        // Act & Assert: Active role validation check fails
        assertActionFailsWithReason(initialSetup, GameErrorReason.NOT_TRADE_INITIATOR) {
            it.submitTradeMoney("player-2", setOf("some-card-id"))
        }
    }

    @Test
    fun `submitTradeMoney fails if initiator does not own the submitted money cards`() {
        // Setup: Initiator submits card credentials missing from their active inventory list
        val activeTrade =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
            )
        val initialSetup =
            baselineState.copy(
                phase = GamePhase.TRADE_OFFER,
                tradeState = activeTrade,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-1",
                        moneyValues = listOf(10),
                    ),
            )

        // Act & Assert: Asset ownership requirement condition check fails
        assertActionFailsWithReason(initialSetup, GameErrorReason.NOT_OWNED_MONEY_CARDS) {
            it.submitTradeMoney("player-1", setOf("fake-money-id"))
        }
    }

    @Test
    fun `respondToTrade updates trade information and transitions phase on blind acceptance`() {
        // Setup: Player 1 offers 10 money for a COW. Player 2 accepts blindly.
        val cow1 = AnimalCard("c1", AnimalType.COW)
        val cow2 = AnimalCard("c2", AnimalType.COW)
        val tradeState =
            createTestTradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                animalCards = setOf(cow1, cow2),
                offeredMoney = listOf(10),
                counterOfferedMoney = null,
            )

        // Animal collections start empty because chooseTrade already extracted them
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players =
                    baselineState.players
                        .withPlayerAssets("player-1", moneyValues = emptyList())
                        .withPlayerAssets("player-2", moneyValues = emptyList()),
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Player 2 counteroffers nothing (blind acceptance)
        val updatedState =
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = emptySet())

        // Assert: Phase transitions and registers the timeline window
        assertEquals(GamePhase.TRADE_RESULT, updatedState.phase)
        assertValidTimeout(expectedDuration = PhaseDurations.TRADE_RESULT_MS, state = updatedState)

        // Assert: Trade state retains previous inputs and registers the server-calculated outcome
        val updatedTrade = updatedState.tradeState
        assertNotNull(updatedTrade)
        assertEquals("player-1", updatedTrade.winnerId)
        val counterCards = checkNotNull(updatedTrade.counterOfferedMoneyCards)
        assertTrue(counterCards.isEmpty())

        // Assert: Transaction payout handles instant distribution (Initiator wins tiebreaker)
        val finalInitiator = updatedState.players.find { it.id == "player-1" }!!
        val finalTarget = updatedState.players.find { it.id == "player-2" }!!
        assertEquals(2, finalInitiator.animals.size)
        assertTrue(finalTarget.animals.isEmpty())
        assertEquals(1, finalTarget.moneyCards.size)
        assertEquals(10, finalTarget.totalMoney())
    }

    @Test
    fun `respondToTrade updates trade information and transitions phase on counteroffer`() {
        // Setup: Player 1 offers 10 money. Player 2 counters with 10 money.
        val cow1 = AnimalCard("c1", AnimalType.COW)
        val cow2 = AnimalCard("c2", AnimalType.COW)
        val targetCardId = "player-2-10-1"

        val tradeState =
            createTestTradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                animalCards = setOf(cow1, cow2),
                offeredMoney = listOf(10),
                counterOfferedMoney = null,
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players =
                    baselineState.players
                        .withPlayerAssets(
                            "player-1",
                            moneyValues = emptyList(),
                        ) // Already paid during submit step
                        .withPlayerAssets("player-2", moneyValues = listOf(10)),
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Player 2 counters with 10 money
        val updatedState =
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = setOf(targetCardId))

        // Assert: Phase transitions and registers the timeline window
        assertEquals(GamePhase.TRADE_RESULT, updatedState.phase)
        assertValidTimeout(expectedDuration = PhaseDurations.TRADE_RESULT_MS, state = updatedState)

        // Assert: Trade state tracks updated counter-offer metrics
        val updatedTrade = updatedState.tradeState
        assertNotNull(updatedTrade)
        assertEquals("player-1", updatedTrade.winnerId)
        val targetMoneyCards = updatedTrade.counterOfferedMoneyCards
        assertNotNull(targetMoneyCards)
        assertEquals(1, targetMoneyCards.size)
        assertTrue(targetMoneyCards.any { it.id == targetCardId })

        // Assert: Transaction payout distributes assets (Initiator wins the tie)
        val finalInitiator = updatedState.players.find { it.id == "player-1" }!!
        val finalTarget = updatedState.players.find { it.id == "player-2" }!!
        assertEquals(2, finalInitiator.animals.size)
        assertTrue(finalTarget.animals.isEmpty())

        // Money cards are completely swapped between participants
        assertEquals(10, finalInitiator.totalMoney())
        assertEquals(10, finalTarget.totalMoney())
        assertTrue(finalInitiator.moneyCards.any { it.id == targetCardId })
    }

    @Test
    fun `respondToTrade fails if actor is not in room`() {
        assertFailsWithMissingActor(baselineState.copy(phase = GamePhase.TRADE_RESPONSE)) {
            it.respondToTrade("nonexistent player", counterOfferedMoneyCardIds = emptySet())
        }
    }

    @Test
    fun `respondToTrade fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.PLAYER_CHOICE)) {
            it.respondToTrade("player-2", counterOfferedMoneyCardIds = emptySet())
        }
    }

    @Test
    fun `respondToTrade throws IllegalStateException if trade state is missing`() {
        // Setup: Active phase configured but lacking critical data node
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = null,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Assert: Exception is thrown
        assertThrows<IllegalStateException> {
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = emptySet())
        }
    }

    @Test
    fun `respondToTrade fails if actor is not the trade target`() {
        // Setup: Active trade state where Player 2 is the valid challenger target
        val tradeState =
            createTestTradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                offeredMoney = listOf(10),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
            )

        // Act & Assert: Player 3 tries to inject validation parameters out of bounds
        assertActionFailsWithReason(activeState, GameErrorReason.NOT_TRADE_TARGET) {
            it.respondToTrade("player-3", counterOfferedMoneyCardIds = emptySet())
        }
    }

    @Test
    fun `respondToTrade throws IllegalStateException if initiator money offer is missing`() {
        // Setup: Active response phase, but initiator cards are unpopulated (null)
        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                offeredMoneyCards = null, // Breaking constraint trigger
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act & Assert: Intentional server assertion fails via checkNotNull
        assertThrows<IllegalStateException> {
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = emptySet())
        }
    }

    @Test
    fun `respondToTrade throws IllegalStateException if initiator is missing from player list`() {
        // Setup: Active trade state where the initiator has suddenly dropped out of the session players list
        val tradeState =
            createTestTradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                offeredMoney = listOf(10),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players = listOf(Player(id = "player-2", name = "Player 2")), // Player 1 is missing
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act & Assert: .updatePlayer wrapper throws state tracking error
        assertThrows<IllegalStateException> {
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = emptySet())
        }
    }

    @Test
    fun `respondToTrade fails if target does not own the counter offered money cards`() {
        // Setup: Target player only has a 10 euro card but attempts to challenge with a different card index
        val tradeState =
            createTestTradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                offeredMoney = listOf(10),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-2",
                        moneyValues = listOf(10),
                    ),
            )

        // Act & Assert: Asset presence assertion fails
        assertActionFailsWithReason(activeState, GameErrorReason.NOT_OWNED_MONEY_CARDS) {
            it.respondToTrade("player-2", counterOfferedMoneyCardIds = setOf("fake-money-id"))
        }
    }

    @Test
    fun `spy successfully adds spying information`() {
        // Player 2 is spying on Player 3
        val spyingState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0, // player-1 is active
                activeSpies = emptySet(),
                spiedThisTurn = emptySet(),
                players =
                    baselineState.players
                        .withPlayerAssets("player-2", moneyValues = listOf(10)) // Spy has money
                        .withPlayerAssets(
                            "player-3",
                            moneyValues = listOf(50, 100, 20, 10, 0),
                        ),
                // Target has money
            )
        val session = GameSession.fromState("game-1", spyingState)

        // Act
        val updatedState = session.spy(actorId = "player-2", targetId = "player-3")

        // Verify: The spy action tracking record was stored correctly
        assertEquals(1, updatedState.activeSpies.size)
        val activeSpy = updatedState.activeSpies.first()
        assertEquals("player-2", activeSpy.spyId)
        assertEquals("player-3", activeSpy.targetId)

        // Verify: Selected cards are drawn from the targets real money card hand
        assertEquals(GameSession.SPYING_CARDS_REVEALED, activeSpy.revealedCards.size)
        val targetMoney = updatedState.players.find { it.id == "player-3" }!!.moneyCards
        assertTrue(targetMoney.containsAll(activeSpy.revealedCards))

        // Verify: Cooldown tracking set up
        assertTrue(updatedState.spiedThisTurn.contains("player-2"))
    }

    @Test
    fun `spy fails if actor is not in room`() {
        val state = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        assertActionFailsWithReason(state, GameErrorReason.UNKNOWN_ACTOR) {
            it.spy(actorId = "nonexistent player", targetId = "player-3")
        }
    }

    @Test
    fun `spy fails if phase is invalid`() {
        val state = baselineState.copy(phase = GamePhase.AUCTION_BIDDING)
        assertActionFailsWithReason(state, GameErrorReason.INVALID_PHASE) {
            it.spy(actorId = "player-2", targetId = "player-3")
        }
    }

    @Test
    fun `spy fails if actor is the currently active player`() {
        val state =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 1, // Player 2 is the active turn player
            )
        assertActionFailsWithReason(state, GameErrorReason.ACTIVE_PLAYER_CANNOT_SPY) {
            it.spy(actorId = "player-2", targetId = "player-3")
        }
    }

    @Test
    fun `spy fails if actor has already spied during this turn cycle`() {
        val state =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
                spiedThisTurn = setOf("player-2"), // Player 2 already spied
            )
        assertActionFailsWithReason(state, GameErrorReason.ALREADY_SPIED_THIS_TURN) {
            it.spy(actorId = "player-2", targetId = "player-3")
        }
    }

    @Test
    fun `spy fails if target player does not exist`() {
        val state =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
            )
        assertActionFailsWithReason(state, GameErrorReason.UNKNOWN_TARGET) {
            it.spy(actorId = "player-2", targetId = "nonexistent player")
        }
    }

    @Test
    fun `spy fails if actor targets themselves`() {
        val state =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
            )
        assertActionFailsWithReason(state, GameErrorReason.TARGETING_SELF) {
            it.spy(actorId = "player-2", targetId = "player-2")
        }
    }

    @Test
    fun `spy fails if actor has no money cards`() {
        val state =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
                players =
                    baselineState.players
                        .withPlayerAssets(
                            "player-2",
                            moneyValues = emptyList(),
                        ),
                // No money cards
            )
        assertActionFailsWithReason(state, GameErrorReason.CANNOT_SPY_WITHOUT_MONEY) {
            it.spy(actorId = "player-2", targetId = "player-3")
        }
    }

    @Test
    fun `catchSpy penalizes active spies and awards cards to actor`() {
        val currentTime = System.currentTimeMillis()

        // Setup: Player 2 and Player 3 are spying on Player 1
        val spy1Action =
            SpyAction(
                spyId = "player-2",
                targetId = "player-1",
                expiresAt = currentTime + 5000,
                revealedCards = emptySet(),
            )
        val spy2Action =
            SpyAction(
                spyId = "player-3",
                targetId = "player-1",
                expiresAt = currentTime + 5000,
                revealedCards = emptySet(),
            )

        val catchingState =
            baselineState.copy(
                activeSpies = setOf(spy1Action, spy2Action),
                players =
                    baselineState.players
                        .withPlayerAssets(
                            "player-1",
                            moneyValues = listOf(10),
                        ) // Actor starts with 1 card
                        .withPlayerAssets("player-2", moneyValues = listOf(50))
                        .withPlayerAssets("player-3", moneyValues = listOf(100)),
            )
        val session = GameSession.fromState("game-1", catchingState)

        // Act
        val updatedState = session.catchSpy("player-1")

        // Verify: The active spies lost their cards
        val updatedSpy1 = updatedState.players.find { it.id == "player-2" }!!
        val updatedSpy2 = updatedState.players.find { it.id == "player-3" }!!
        assertTrue(updatedSpy1.moneyCards.isEmpty())
        assertTrue(updatedSpy2.moneyCards.isEmpty())

        // Verify: Actor gained both stolen cards (Total: 10 + 50 + 100)
        val updatedActor = updatedState.players.find { it.id == "player-1" }!!
        assertEquals(3, updatedActor.moneyCards.size)

        // Verify: The money cards match the taken assets
        val totalMoneyValues = updatedActor.moneyCards.map { it.value }
        assertTrue(totalMoneyValues.containsAll(listOf(10, 50, 100)))

        // Verify: Caught spy actions are dropped from active tracking
        assertTrue(updatedState.activeSpies.isEmpty())
    }

    @Test
    fun `catchSpy fails if actor is not in room`() {
        assertActionFailsWithReason(baselineState, GameErrorReason.UNKNOWN_ACTOR) {
            it.catchSpy("nonexistent player")
        }
    }

    @Test
    fun `catchSpy fails if actor is spying`() {
        // Setup: Player 1 tries to catch players, but Player 1 is currently busy spying on Player 3
        val runningSpy =
            SpyAction(
                spyId = "player-1",
                targetId = "player-3",
                expiresAt = System.currentTimeMillis() + 5000,
                revealedCards = emptySet(),
            )
        val state =
            baselineState.copy(
                activeSpies = setOf(runningSpy),
            )

        assertActionFailsWithReason(state, GameErrorReason.CANNOT_CATCH_WHILE_SPYING) {
            it.catchSpy("player-1")
        }
    }

    @Test
    fun `catchSpy fails if no valid spies target the actor`() {
        val currentTime = System.currentTimeMillis()

        // Setup: There is an active spy, but they are targeting Player 3, not Player 1
        val unrelatedSpy =
            SpyAction(
                spyId = "player-2",
                targetId = "player-3",
                expiresAt = currentTime + 5000,
                revealedCards = emptySet(),
            )
        // Setup: Player 2 also has an expired spy targeting Player 1
        val expiredSpy =
            SpyAction(
                spyId = "player-2",
                targetId = "player-1",
                expiresAt = currentTime - 10000,
                revealedCards = emptySet(),
            )

        val catchingState =
            baselineState.copy(
                activeSpies = setOf(unrelatedSpy, expiredSpy),
                players =
                    baselineState.players
                        .withPlayerAssets("player-1", moneyValues = listOf(10))
                        .withPlayerAssets("player-2", moneyValues = listOf(50)),
            )

        assertActionFailsWithReason(catchingState, GameErrorReason.NOT_SPIED_UPON) {
            it.catchSpy("player-1")
        }
    }

    @Test
    fun `catchSpy throws IllegalStateException if an active spy is missing from players list`() {
        val currentTime = System.currentTimeMillis()

        // Setup: Player 2 is spying on Player 1
        val corruptedSpyAction =
            SpyAction(
                spyId = "player-2",
                targetId = "player-1",
                expiresAt = currentTime + 5000,
                revealedCards = emptySet(),
            )
        val corruptedState =
            baselineState.copy(
                activeSpies = setOf(corruptedSpyAction),
                players =
                    listOf(
                        Player(
                            id = "player-1",
                            name = "Player 1",
                            moneyCards = listOf(MoneyCard(id = "m1", value = 10)),
                        ),
                        // The spy player-2 is missing from the player list
                    ),
            )
        val session = GameSession.fromState("game-1", corruptedState)

        assertThrows<IllegalStateException> {
            session.catchSpy("player-1")
        }
    }

    @Test
    fun `catchSpy throws IllegalStateException if caught spy has no money cards`() {
        val currentTime = System.currentTimeMillis()

        // Setup: Player 2 is spying on Player 1
        val spyAction =
            SpyAction(
                spyId = "player-2",
                targetId = "player-1",
                expiresAt = currentTime + 5000,
                revealedCards = emptySet(),
            )

        // Setup: Player 2 has no money cards
        val corruptedState =
            baselineState.copy(
                activeSpies = setOf(spyAction),
                players =
                    baselineState.players
                        .withPlayerAssets("player-1", moneyValues = listOf(10))
                        .withPlayerAssets("player-2", moneyValues = emptyList()),
                // Breaking constraint
            )
        val session = GameSession.fromState("game-1", corruptedState)

        assertThrows<IllegalStateException> {
            session.catchSpy("player-1")
        }
    }

    @Test
    fun `makeDefaultPlayerChoice skips player turn when player choice phase expires`() {
        // Setup: Active turn state with Player 1 frozen at the wheel
        val activeState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 0,
                roundNumber = 5,
                activeSpies =
                    setOf(
                        SpyAction(
                            "player-2",
                            "player-1",
                            System.currentTimeMillis() + 1000L,
                            setOf(MoneyCard("money-20-1", 20)),
                        ),
                    ),
                spiedThisTurn = setOf("player-2"),
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Advances loop to next seat and stays in player choice room with an automated clock
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.currentPlayerIndex)
        assertEquals(6, updatedState.roundNumber)
        assertValidTimeout(expectedDuration = PhaseDurations.PLAYER_CHOICE_MS, state = updatedState)
        assertEquals(emptySet(), updatedState.activeSpies)
        assertEquals(emptySet(), updatedState.spiedThisTurn)
    }

    @Test
    fun `makeDefaultAuctioneerDecision sells when auctioneer decision phase expires`() {
        // Setup: Auctioneer is Player 1. Player 2 is high bidder with 20.
        val targetCard = AnimalCard("cow-1", AnimalType.COW)
        val auctionState =
            AuctionState(
                auctionCard = targetCard,
                auctioneerId = "player-1",
                highestBid = 20,
                highestBidderId = "player-2",
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-2",
                        moneyValues = listOf(10, 10),
                    ),
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Cascades to resolveAuction, which prompts the high bidder to pay
        assertEquals(GamePhase.AUCTION_PAYMENT, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_PAYMENT_MS,
            state = updatedState,
        )
        assertEquals("player-2", updatedState.auctionState?.buyerId)
        assertEquals("player-1", updatedState.auctionState?.sellerId)
    }

    @Test
    fun `makeDefaultAuctioneerDecision throws IllegalStateException if auction state is missing`() {
        val brokenState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = null,
            )
        val session = GameSession.fromState("game-1", brokenState)

        assertThrows<IllegalStateException> { session.handleTimeoutExpiration() }
    }

    /**
     * Builds an auction state parked in [GamePhase.AUCTION_PAYMENT] where player-2 (the buyer)
     * owes 20 to player-1 (the seller) and holds two 10 money cards.
     */
    private fun auctionPaymentState() =
        baselineState.copy(
            phase = GamePhase.AUCTION_PAYMENT,
            auctionState =
                AuctionState(
                    auctionCard = AnimalCard("cow-1", AnimalType.COW),
                    auctioneerId = "player-1",
                    highestBid = 20,
                    highestBidderId = "player-2",
                    buyerId = "player-2",
                    sellerId = "player-1",
                ),
            players =
                baselineState.players.withPlayerAssets(
                    "player-2",
                    moneyValues = listOf(10, 10),
                ),
        )

    @Test
    fun `submitAuctionPayment transfers selected cards and awards the animal`() {
        val session = GameSession.fromState("game-1", auctionPaymentState())
        val buyerCardIds =
            session.state.players
                .find { it.id == "player-2" }!!
                .moneyCards
                .map { it.id }
                .toSet()

        val updatedState = session.submitAuctionPayment("player-2", buyerCardIds)

        val seller = updatedState.players.find { it.id == "player-1" }!!
        val buyer = updatedState.players.find { it.id == "player-2" }!!
        assertTrue(buyer.animals.any { it.id == "cow-1" })
        assertTrue(buyer.moneyCards.isEmpty())
        assertEquals(20, seller.totalMoney())
        assertEquals(GamePhase.AUCTION_RESULT, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_RESULT_MS,
            state = updatedState,
        )
    }

    @Test
    fun `submitAuctionPayment fails when the selection does not cover the bid`() {
        val session = GameSession.fromState("game-1", auctionPaymentState())
        val singleCardId =
            session.state.players
                .find { it.id == "player-2" }!!
                .moneyCards
                .first()
                .id

        assertActionFailsWithReason(auctionPaymentState(), GameErrorReason.NOT_ENOUGH_MONEY) {
            it.submitAuctionPayment("player-2", setOf(singleCardId))
        }
    }

    @Test
    fun `submitAuctionPayment fails if actor is not the buyer`() {
        assertActionFailsWithReason(auctionPaymentState(), GameErrorReason.NOT_AUCTIONEER) {
            it.submitAuctionPayment("player-1", emptySet())
        }
    }

    @Test
    fun `submitAuctionPayment fails if phase is invalid`() {
        assertFailsWithInvalidPhase(baselineState.copy(phase = GamePhase.AUCTIONEER_DECISION)) {
            it.submitAuctionPayment("player-2", emptySet())
        }
    }

    @Test
    fun `auction payment timeout auto-selects the buyer's money cards`() {
        val session = GameSession.fromState("game-1", auctionPaymentState())

        val updatedState = session.handleTimeoutExpiration()

        val seller = updatedState.players.find { it.id == "player-1" }!!
        val buyer = updatedState.players.find { it.id == "player-2" }!!
        assertTrue(buyer.animals.any { it.id == "cow-1" })
        assertEquals(20, seller.totalMoney())
        assertEquals(0, buyer.totalMoney())
        assertEquals(GamePhase.AUCTION_RESULT, updatedState.phase)
    }

    @Test
    fun `endAuctionSequence clears auction track block when auction result screen expires`() {
        // Setup: Active auction result phase showing final auction outcomes
        val activeState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESULT,
                auctionState = AuctionState(AnimalCard("c1", AnimalType.COW), "player-1"),
                currentPlayerIndex = 0,
                roundNumber = 3,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Completely sweeps auction information and sets up next turn loop
        assertNull(updatedState.auctionState)
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.currentPlayerIndex)
        assertValidTimeout(expectedDuration = PhaseDurations.PLAYER_CHOICE_MS, state = updatedState)
    }

    @Test
    fun `makeDefaultTradeOffer submits empty offer when trade offer phase expires`() {
        // Setup: Active trade state where the initiator fails to submit cash choices
        val activeTrade =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                animalCards = setOf(AnimalCard("c1", AnimalType.COW)),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_OFFER,
                tradeState = activeTrade,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-1",
                        moneyValues = listOf(10),
                    ),
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Cascades to submitTradeMoney with an empty set and hits response room
        assertEquals(GamePhase.TRADE_RESPONSE, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.TRADE_RESPONSE_MS,
            state = updatedState,
        )
        assertNotNull(updatedState.tradeState?.offeredMoneyCards)
        assertTrue(updatedState.tradeState!!.offeredMoneyCards!!.isEmpty())
    }

    @Test
    fun `makeDefaultTradeOffer throws IllegalStateException if trade state is missing`() {
        val brokenState =
            baselineState.copy(
                phase = GamePhase.TRADE_OFFER,
                tradeState = null,
            )
        val session = GameSession.fromState("game-1", brokenState)

        assertThrows<IllegalStateException> { session.handleTimeoutExpiration() }
    }

    @Test
    fun `makeDefaultTradeResponse accepts blindly when trade response phase expires`() {
        // Setup: Active trade state where target fails to execute a counter challenge
        val activeTrade =
            createTestTradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                animalCards =
                    setOf(
                        AnimalCard("c1", AnimalType.COW),
                        AnimalCard("c2", AnimalType.COW),
                    ),
                offeredMoney = listOf(10),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = activeTrade,
                players =
                    baselineState.players
                        .withPlayerAssets("player-1", moneyValues = emptyList())
                        .withPlayerAssets("player-2", moneyValues = listOf(10)),
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Cascades to respondToTrade with empty set to trigger resolution outcome
        assertEquals(GamePhase.TRADE_RESULT, updatedState.phase)
        assertValidTimeout(expectedDuration = PhaseDurations.TRADE_RESULT_MS, state = updatedState)
        assertEquals("player-1", updatedState.tradeState?.winnerId)
    }

    @Test
    fun `makeDefaultTradeResponse throws IllegalStateException if trade state is missing`() {
        val brokenState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = null,
            )
        val session = GameSession.fromState("game-1", brokenState)

        assertThrows<IllegalStateException> { session.handleTimeoutExpiration() }
    }

    @Test
    fun `endTradeSequence clears trade state and advances the turn loop on happy path`() {
        // Setup: Active trade result phase with a mock trade state attached
        val tradeState = createTestTradeState(initiatorId = "player-1", targetId = "player-2")
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESULT,
                tradeState = tradeState,
                currentPlayerIndex = 0,
                roundNumber = 5,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: The trade state tracking node has been completely cleared out
        assertNull(updatedState.tradeState)

        // Assert: Turn progression is triggered and sets the next turn's timer
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.currentPlayerIndex)
        assertEquals(6, updatedState.roundNumber)
        assertValidTimeout(expectedDuration = PhaseDurations.PLAYER_CHOICE_MS, state = updatedState)
    }

    @Test
    fun `handleTimeoutExpiration throws IllegalStateException when the phase is untimed`() {
        val nonTimedState = baselineState.copy(phase = GamePhase.NOT_STARTED)
        val session = GameSession.fromState("game-1", nonTimedState)

        // Assert: Rejects processing ticks outside active round play loops
        assertThrows<IllegalStateException> { session.handleTimeoutExpiration() }
    }

    @Test
    fun `game end is detected when all quartets are formed`() {
        // Setup: Almost all quartets are formed. One card left to form the last quartet.
        val lastAnimalType = AnimalType.entries.last()
        val playersWithQuartets =
            baselineState.players.mapIndexed { index, player ->
                if (index == 0) {
                    // Give player 1 all animal types except the last one, each with 4 cards
                    val animals =
                        AnimalType.entries.filter { it != lastAnimalType }.flatMap { type ->
                            (1..4).map { AnimalCard("${type.name}-$it", type) }
                        }
                    player.copy(animals = animals)
                } else {
                    player
                }
            }

        // Target: Set up an active bid where Player 1 (auctioneer) needs to buy back the 4th card
        val lastCard = AnimalCard("${lastAnimalType.name}-4", lastAnimalType)
        val player1With3Cards =
            playersWithQuartets[0].copy(
                animals =
                    playersWithQuartets[0].animals +
                        (1..3).map { AnimalCard("${lastAnimalType.name}-$it", lastAnimalType) },
                moneyCards = createDummyMoney("player-1", listOf(50)),
            )
        val finalPlayers = listOf(player1With3Cards) + playersWithQuartets.drop(1)

        val auctionState =
            AuctionState(
                auctionCard = lastCard,
                auctioneerId = "player-1",
                highestBid = 10,
                highestBidderId = "player-2",
            )

        val decisionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                players = finalPlayers,
                auctionState = auctionState,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", decisionState)

        // Act: Auctioneer manually executes buy-back to claim the final quartet card
        val paymentState = session.resolveAuction("player-1", auctioneerBuysCard = true)
        assertEquals(GamePhase.AUCTION_PAYMENT, paymentState.phase)

        // Act: Auctioneer pays for the card, entering the result phase
        val buyerCardIds =
            paymentState.players
                .find { it.id == "player-1" }!!
                .moneyCards
                .map { it.id }
                .toSet()
        val stateAfterResolution = session.submitAuctionPayment("player-1", buyerCardIds)

        // Assert: Verify the game enters the result phase successfully
        assertEquals(GamePhase.AUCTION_RESULT, stateAfterResolution.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_RESULT_MS,
            state = stateAfterResolution,
        )

        // Act: Simulate the phase timer running down to zero
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Game loop terminates cleanly and transitions directly to the end game screen
        assertEquals(GamePhase.FINISHED, updatedState.phase)
        assertNull(updatedState.timerEnd)
        assertNull(updatedState.lastEvent)
    }

    @Test
    fun `calculateTimeoutForActor selects shorter timeout when actor is disconnected`() {
        // Setup: Active turn state with Player 2 disconnected
        val activeState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players =
                    listOf(
                        Player(id = "player-1", name = "Player 1"),
                        Player(id = "player-2", name = "Player 2", isConnected = false),
                        Player(id = "player-3", name = "Player 3"),
                    ),
                currentPlayerIndex = 0,
                roundNumber = 5,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Execute via the master timeout gateway
        val updatedState = session.handleTimeoutExpiration()

        // Assert: Advances loop to next seat and sets a shorter timeout for the disconnected player
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.currentPlayerIndex)
        assertEquals(6, updatedState.roundNumber)
        assertValidTimeout(
            expectedDuration = PhaseDurations.DISCONNECTED_TURN_DURATION_MS,
            state = updatedState,
        )
    }

    @Test
    fun `selectMoneyCardsForPayment chooses optimal overpayment`() {
        // Setup: Player 1 has 50, 10, 10 and needs to satisfy a bid of 15. Optimal combo is 10+10=20.
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard("c1", AnimalType.COW),
                auctioneerId = "player-2",
                highestBid = 15,
                highestBidderId = "player-1",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTIONEER_DECISION,
                auctionState = auctionState,
                players =
                    baselineState.players.withPlayerAssets(
                        "player-1",
                        moneyValues = listOf(50, 10, 10),
                    ),
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: Resolve the auction, then let the payment timer expire so the server auto-picks
        session.resolveAuction("player-2", auctioneerBuysCard = false)
        val updatedState = session.handleTimeoutExpiration()

        val buyer = updatedState.players.find { it.id == "player-1" }!!
        val seller = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Buyer pays exactly two 10s (leaving them with just the 50 card)
        assertEquals(1, buyer.moneyCards.size)
        assertEquals(50, buyer.totalMoney())
        assertEquals(2, seller.moneyCards.size)
        assertEquals(20, seller.totalMoney())

        // Assert: State shifts to auction result phase and locks in the buyer identity
        assertEquals(GamePhase.AUCTION_RESULT, updatedState.phase)
        assertValidTimeout(
            expectedDuration = PhaseDurations.AUCTION_RESULT_MS,
            state = updatedState,
        )

        val finalAuction = updatedState.auctionState
        assertNotNull(finalAuction)
        assertEquals("player-1", finalAuction.buyerId)
    }

    @Test
    fun `hasPlayer returns true when player is in the session`() {
        val gameSession = GameSession.fromState("game-1", baselineState)

        assertTrue(gameSession.hasPlayer("player-1"))
    }

    @Test
    fun `hasPlayer returns false when player is not in the session`() {
        val gameSession = GameSession.fromState("game-1", baselineState)

        assertFalse(gameSession.hasPlayer("player-4"))
    }

    @Test
    fun `getEarliestSpyExpiration returns the lowest timestamp among active spies`() {
        val now = System.currentTimeMillis()
        val spyActions =
            setOf(
                SpyAction(
                    spyId = "player-2",
                    targetId = "player-1",
                    expiresAt = now + 5000L,
                    revealedCards = emptySet(),
                ),
                SpyAction(
                    spyId = "player-3",
                    targetId = "player-1",
                    expiresAt = now + 2000L,
                    revealedCards = emptySet(),
                ),
                SpyAction(
                    spyId = "player-5",
                    targetId = "player-1",
                    expiresAt = now + 8000L,
                    revealedCards = emptySet(),
                ),
            )
        val stateWithSpies = baselineState.copy(activeSpies = spyActions)
        val gameSession = GameSession.fromState("game-1", stateWithSpies)

        assertEquals(now + 2000L, gameSession.getEarliestSpyExpiration())
    }

    @Test
    fun `getEarliestSpyExpiration returns null when there are no active spies`() {
        val stateWithoutSpies = baselineState.copy(activeSpies = emptySet())
        val gameSession = GameSession.fromState("game-1", stateWithoutSpies)

        assertNull(gameSession.getEarliestSpyExpiration())
    }

    @Test
    fun `hasActiveSpies returns true when there is at least one active spy`() {
        val spyActions =
            setOf(
                SpyAction(
                    spyId = "player-2",
                    targetId = "player-1",
                    expiresAt = System.currentTimeMillis() + 5000L,
                    revealedCards = emptySet(),
                ),
            )
        val stateWithSpies = baselineState.copy(activeSpies = spyActions)
        val gameSession = GameSession.fromState("game-1", stateWithSpies)

        assertTrue(gameSession.hasActiveSpies())
    }

    @Test
    fun `hasActiveSpies returns false when there are no active spies`() {
        val stateWithoutSpies = baselineState.copy(activeSpies = emptySet())
        val gameSession = GameSession.fromState("game-1", stateWithoutSpies)

        assertFalse(gameSession.hasActiveSpies())
    }

    @Test
    fun `clearExpiredSpies purges expired spy actions`() {
        val pastTime = System.currentTimeMillis() - 5000L
        val futureTime = System.currentTimeMillis() + 10000L

        val expiredSpy =
            SpyAction(
                spyId = "player-2",
                targetId = "player-1",
                expiresAt = pastTime,
                revealedCards = emptySet(),
            )
        val validSpy =
            SpyAction(
                spyId = "player-3",
                targetId = "player-1",
                expiresAt = futureTime,
                revealedCards = emptySet(),
            )

        val initialGameState = baselineState.copy(activeSpies = setOf(expiredSpy, validSpy))
        val gameSession = GameSession.fromState("game-1", initialGameState)

        val resultState = gameSession.clearExpiredSpies()

        assertNotSame(initialGameState, resultState)
        assertEquals(1, resultState.activeSpies.size)
        assertTrue(resultState.activeSpies.contains(validSpy))
        assertFalse(resultState.activeSpies.contains(expiredSpy))
    }

    @Test
    fun `clearExpiredSpies does not mutate the state reference when no spies have expired`() {
        val futureTime = System.currentTimeMillis() + 10000L
        val spyActions =
            setOf(
                SpyAction(
                    spyId = "player-2",
                    targetId = "player-1",
                    expiresAt = futureTime,
                    revealedCards = emptySet(),
                ),
            )
        val initialGameState = baselineState.copy(activeSpies = spyActions)
        val gameSession = GameSession.fromState("game-1", initialGameState)

        val resultState = gameSession.clearExpiredSpies()

        assertSame(initialGameState, resultState)
        assertEquals(1, gameSession.state.activeSpies.size)
    }

    private fun assertValidTimeout(
        expectedDuration: Long,
        state: GameState,
    ) {
        val actualTimestamp = state.timerEnd
        val tolerance = 2000L

        checkNotNull(actualTimestamp) { "Expected a phase timeout window, but timerEnd was null" }
        val expectedTime = System.currentTimeMillis() + expectedDuration
        val diff = kotlin.math.abs(actualTimestamp - expectedTime)
        assertTrue(
            diff <= tolerance,
            "Timeout timestamp $actualTimestamp fell outside window ($expectedTime +/- ${tolerance}ms)",
        )
    }

    private fun assertFailsWithMissingActor(
        configuredState: GameState = baselineState,
        actionCall: (GameSession) -> Unit,
    ) {
        assertActionFailsWithReason(configuredState, GameErrorReason.UNKNOWN_ACTOR) {
            actionCall(it)
        }
    }

    private fun assertFailsWithInvalidPhase(
        configuredState: GameState = baselineState,
        actionCall: (GameSession) -> Unit,
    ) {
        assertActionFailsWithReason(configuredState, GameErrorReason.INVALID_PHASE) {
            actionCall(it)
        }
    }

    private fun assertActionFailsWithReason(
        configuredState: GameState = baselineState,
        expectedReason: GameErrorReason,
        actionCall: (GameSession) -> Unit,
    ) {
        val session = GameSession.fromState("game-1", configuredState)
        val exception = assertThrows<GameException> { actionCall(session) }
        assertEquals(expectedReason, exception.reason)
    }

    private fun List<Player>.withPlayerAssets(
        id: String,
        moneyValues: List<Int> = emptyList(),
        animals: List<AnimalCard> = emptyList(),
    ): List<Player> =
        this.map { player ->
            if (player.id == id) {
                player.copy(
                    moneyCards =
                        if (moneyValues.isNotEmpty()) {
                            createDummyMoney(
                                id,
                                moneyValues,
                            )
                        } else {
                            player.moneyCards
                        },
                    animals = player.animals + animals,
                )
            } else {
                player
            }
        }

    private fun createTestTradeState(
        initiatorId: String = "player-1",
        targetId: String = "player-2",
        animalCards: Set<AnimalCard> = emptySet(),
        offeredMoney: List<Int> = listOf(10), // Default mock values
        counterOfferedMoney: List<Int>? = null,
    ): TradeState {
        val offeredSet = createDummyMoney(initiatorId, offeredMoney).toSet()
        val counterSet = counterOfferedMoney?.let { createDummyMoney(targetId, it).toSet() }

        return TradeState(
            initiatorId = initiatorId,
            targetId = targetId,
            requestedAnimalType =
                animalCards.firstOrNull()?.type
                    ?: AnimalType.DONKEY,
            // Legacy field
            animalCards = animalCards,
            offeredMoneyCards = offeredSet,
            counterOfferedMoneyCards = counterSet,
        )
    }

    // Helper to generate a dummy money card list easily
    private fun createDummyMoney(
        playerId: String,
        values: List<Int>,
    ): List<MoneyCard> =
        values.mapIndexed { index, value ->
            MoneyCard(id = "$playerId-$value-${index + 1}", value = value)
        }

    private companion object {
        const val CARDS_PER_ANIMAL_TYPE = 4
        val FULL_DECK_SIZE = AnimalType.entries.size * CARDS_PER_ANIMAL_TYPE
        val STARTING_MONEY_VALUES = listOf(0, 0, 10, 10, 10, 10, 50)
    }
}
