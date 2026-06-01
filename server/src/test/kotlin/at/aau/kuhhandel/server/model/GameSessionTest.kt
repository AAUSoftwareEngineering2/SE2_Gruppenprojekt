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
                    PlayerState(id = "player-1", name = "Player 1"),
                    PlayerState(id = "player-2", name = "Player 2"),
                    PlayerState(id = "player-3", name = "Player 3"),
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
        val session = GameSession.fromState("game-1", baselineState)

        val exception =
            assertThrows<GameException> {
                session.addPlayer("player-1", "Player 4")
            }
        assertEquals(GameErrorReason.ALREADY_IN_ROOM, exception.reason)
    }

    @Test
    fun `addPlayer fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)

        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.addPlayer("player-4", "Player 4")
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `addPlayer fails if room is full`() {
        val brokenState =
            baselineState.copy(
                players =
                    baselineState.players +
                        PlayerState(id = "player-4", "Player-4") +
                        PlayerState(id = "player-5", "Player 5"),
            )

        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.addPlayer("player-6", "Player 6")
            }
        assertEquals(GameErrorReason.ROOM_FULL, exception.reason)
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
                players = listOf(PlayerState(id = "player-1", name = "player-1")),
                hostPlayerId = "player-1",
            )
        val session = GameSession.fromState("game-1", singlePlayerState)

        val updatedState = session.removePlayer("player-1")

        assertTrue(updatedState.players.isEmpty())
        assertNull(updatedState.hostPlayerId)
    }

    @Test
    fun `removePlayer fails if player is not in room`() {
        val session = GameSession.fromState("game-1", baselineState)

        val exception =
            assertThrows<GameException> {
                session.removePlayer("nonexistent player")
            }
        assertEquals(GameErrorReason.UNKNOWN_ACTOR, exception.reason)
    }

    @Test
    fun `removePlayer fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.removePlayer("player-2")
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `startGame shuffles players, distributes initial money, and transitions phase`() {
        val session = GameSession.fromState("game-1", baselineState)

        val updatedState = session.startGame("player-1")

        // Phase, Round, and Starting Turn assertions
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.roundNumber)
        assertEquals(0, updatedState.currentPlayerIndex)

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
        val session = GameSession.fromState("game-1", baselineState)

        val exception =
            assertThrows<GameException> {
                session.startGame("nonexistent player")
            }
        assertEquals(GameErrorReason.UNKNOWN_ACTOR, exception.reason)
    }

    @Test
    fun `startGame fails if actor is not the host`() {
        val session = GameSession.fromState("game-1", baselineState)

        val exception =
            assertThrows<GameException> {
                session.startGame("player-2")
            }
        assertEquals(GameErrorReason.NOT_HOST, exception.reason)
    }

    @Test
    fun `startGame fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.startGame("player-1")
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `startGame fails if there are not enough players`() {
        val lowPlayerState =
            baselineState.copy(
                players =
                    listOf(
                        PlayerState(id = "player-1", name = "Player 1"),
                        PlayerState(id = "player-2", name = "Player 2"),
                    ),
            )
        val session = GameSession.fromState("game-1", lowPlayerState)

        val exception =
            assertThrows<GameException> {
                session.startGame("player-1")
            }
        assertEquals(GameErrorReason.NOT_ENOUGH_PLAYERS, exception.reason)
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

        val beforeTime = System.currentTimeMillis()
        val updatedState = session.chooseAuction("player-1")
        val afterTime = System.currentTimeMillis()

        // Phase & Card drawing assertions
        assertEquals(GamePhase.AUCTION_BIDDING, updatedState.phase)
        assertEquals(expectedCard, updatedState.currentFaceUpCard)
        assertEquals(1, updatedState.deck.cards.size) // One card remaining

        // Auction state initialization assertions
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(expectedCard, auction.auctionCard)
        assertEquals("player-1", auction.auctioneerId)
        assertEquals(0, auction.highestBid)
        assertNull(auction.highestBidderId)

        // Verify the 5-second timer fallback matches within our execution bound
        val expectedEndTimeLower = beforeTime + 5000L
        val expectedEndTimeUpper = afterTime + 5000L
        val actualEndTime = auction.timerEndTime ?: 0L
        assertTrue(actualEndTime in expectedEndTimeLower..expectedEndTimeUpper)
    }

    @Test
    fun `chooseAuction fails if actor is not in room`() {
        val session =
            GameSession.fromState("game-1", baselineState.copy(phase = GamePhase.PLAYER_CHOICE))

        val exception =
            assertThrows<GameException> {
                session.chooseAuction("nonexistent player")
            }
        assertEquals(GameErrorReason.UNKNOWN_ACTOR, exception.reason)
    }

    @Test
    fun `chooseAuction fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.NOT_STARTED)
        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.chooseAuction("player-1")
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `chooseAuction fails if actor is not the active player`() {
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 1, // It's player-2's turn
            )
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseAuction("player-1") // Player 1 tries to act out of turn
            }
        assertEquals(GameErrorReason.NOT_YOUR_TURN, exception.reason)
    }

    @Test
    fun `chooseAuction fails if the deck is empty`() {
        val emptyDeckState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                deck = AnimalDeck(emptyList()),
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", emptyDeckState)

        val exception =
            assertThrows<GameException> {
                session.chooseAuction("player-1")
            }
        assertEquals(GameErrorReason.DECK_EMPTY, exception.reason)
    }

    @Test
    fun `placeBid updates highest bid and extends timer`() {
        // Setup: Active auction run by Player 1. Player 2 has 90 total money and bids 20.
        val initialAuction =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 10,
                highestBidderId = "player-3",
            )
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
            )
        // Give Player 2 real money cards to pass the money check
        val stateWithMoney =
            biddingState.copy(
                players =
                    biddingState.players.map {
                        if (it.id == "player-2") {
                            it.copy(
                                moneyCards =
                                    createDummyMoney(
                                        "player-2",
                                        listOf(50, 10),
                                    ),
                            )
                        } else {
                            it
                        }
                    },
            )
        val session = GameSession.fromState("game-1", stateWithMoney)

        val beforeTime = System.currentTimeMillis()
        val updatedState = session.placeBid("player-2", amount = 20)
        val afterTime = System.currentTimeMillis()

        // Verify bidding updates correctly
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(20, auction.highestBid)
        assertEquals("player-2", auction.highestBidderId)

        // Verify the 5-second timer extension
        val expectedEndTimeLower = beforeTime + 5000L
        val expectedEndTimeUpper = afterTime + 5000L
        val actualEndTime = auction.timerEndTime ?: 0L
        assertTrue(actualEndTime in expectedEndTimeLower..expectedEndTimeUpper)
    }

    @Test
    fun `placeBid fails if actor is not in room`() {
        val session =
            GameSession.fromState("game-1", baselineState.copy(phase = GamePhase.AUCTION_BIDDING))

        val exception =
            assertThrows<GameException> {
                session.placeBid("nonexistent player", amount = 10)
            }
        assertEquals(GameErrorReason.UNKNOWN_ACTOR, exception.reason)
    }

    @Test
    fun `placeBid fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.placeBid("player-2", amount = 10)
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `placeBid fails if actor is the auctioneer`() {
        val initialAuction =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 0,
                highestBidderId = null,
            )
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
            )
        val session = GameSession.fromState("game-1", biddingState)

        val exception =
            assertThrows<GameException> {
                // Auctioneer trying to bid on their own card
                session.placeBid("player-1", amount = 10)
            }
        assertEquals(GameErrorReason.OWN_AUCTION, exception.reason)
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

        val poorPlayerState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
                players =
                    baselineState.players.map {
                        if (it.id == "player-2") {
                            it.copy(
                                moneyCards =
                                    createDummyMoney(
                                        "player-2",
                                        listOf(smallBillValue),
                                    ),
                            )
                        } else {
                            it
                        }
                    },
            )
        val session = GameSession.fromState("game-1", poorPlayerState)

        val updatedState =
            session.placeBid(
                "player-2",
                amount = illegalBidAmount,
            )

        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(illegalBidAmount, auction.highestBid)
        assertEquals("player-2", auction.highestBidderId)
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
                    baselineState.players.map {
                        // Simple: Player 2 has a 50 card, so they have enough total money,
                        // but their bid of 25 is too low.
                        if (it.id == "player-2") {
                            it.copy(
                                moneyCards = createDummyMoney("player-2", listOf(50)),
                            )
                        } else {
                            it
                        }
                    },
            )
        val session = GameSession.fromState("game-1", biddingState)

        val exception =
            assertThrows<GameException> {
                session.placeBid("player-2", amount = 25) // 25 <= 30, should fail
            }
        assertEquals(GameErrorReason.BID_TOO_LOW, exception.reason)
    }

    @Test
    fun `closeAuctionAfterTimeout handles the edge case if no one placed a bid`() {
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

        val updatedState = session.closeAuctionAfterTimeout()

        // Assert: Auctioneer received the animal card for free
        val auctioneer = updatedState.players.find { it.id == "player-1" }!!
        assertTrue(auctioneer.animals.any { it.id == "cow-1" && it.type == AnimalType.COW })

        // Assert: Transitions to AUCTION_RESOLUTION phase
        assertEquals(GamePhase.AUCTION_RESOLUTION, updatedState.phase)

        // Assert: The auction details remain, but the expiration timer is null
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertNull(auction.highestBidderId)
        assertNull(auction.timerEndTime)
    }

    @Test
    fun `closeAuctionAfterTimeout moves to resolution and awards card when no one bid`() {
        val targetCard = AnimalCard("cow-1", AnimalType.COW)
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState =
                    AuctionState(
                        auctionCard = targetCard,
                        auctioneerId = "player-1",
                        highestBid = 0,
                        highestBidderId = null,
                    ),
                players =
                    listOf(
                        PlayerState(id = "player-1", name = "Player 1"),
                        PlayerState(id = "player-2", name = "Player 2"),
                        PlayerState(id = "player-3", name = "Player 3"),
                    ),
            )
        val session = GameSession.fromState("game-1", biddingState)

        val updatedState = session.closeAuctionAfterTimeout()

        // Assert: Transitions to AUCTION_RESOLUTION phase
        assertEquals(GamePhase.AUCTION_RESOLUTION, updatedState.phase)

        // Assert: The auction details remain, but the expiration timer is null
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertNull(auction.highestBidderId)
        assertNull(auction.timerEndTime)

        // Assert: The card HAS been given to the auctioneer (player-1)
        val auctioneer = updatedState.players.find { it.id == "player-1" }!!
        assertEquals(1, auctioneer.animals.size)
        assertEquals(targetCard, auctioneer.animals.first())
    }

    @Test
    fun `resolveAuction advances turn in zero-bid case`() {
        val targetCard = AnimalCard("cow-1", AnimalType.COW)
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                auctionState =
                    AuctionState(
                        auctionCard = targetCard,
                        auctioneerId = "player-1",
                        highestBid = 0,
                        highestBidderId = null,
                    ),
                players =
                    listOf(
                        PlayerState(
                            id = "player-1",
                            name = "Player 1",
                            animals = listOf(targetCard),
                        ),
                        PlayerState(id = "player-2", name = "Player 2"),
                        PlayerState(id = "player-3", name = "Player 3"),
                    ),
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        val updatedState = session.resolveAuction("player-1", true)

        // Assert: Advances to next turn (PLAYER_CHOICE)
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.currentPlayerIndex) // Next player's turn
        assertNull(updatedState.auctionState)
        assertEquals(baselineState.roundNumber + 1, updatedState.roundNumber)
    }

    @Test
    fun `closeAuctionAfterTimeout functions correctly if a highest bidder exists`() {
        // Setup: Player 2 placed a winning bid of 20
        val testCard = AnimalCard("cow-1", AnimalType.COW)
        val initialAuction =
            AuctionState(
                auctionCard = testCard,
                auctioneerId = "player-1",
                highestBid = 20,
                highestBidderId = "player-2",
                timerEndTime = System.currentTimeMillis() + 1000,
            )
        val biddingState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                auctionState = initialAuction,
            )
        val session = GameSession.fromState("game-1", biddingState)

        val updatedState = session.closeAuctionAfterTimeout()

        // Assert: Transitions directly to the AUCTION_RESOLUTION phase
        assertEquals(GamePhase.AUCTION_RESOLUTION, updatedState.phase)

        // Assert: The auction details remain, but the expiration timer gets cleared out
        val auction = updatedState.auctionState
        assertNotNull(auction)
        assertEquals(20, auction.highestBid)
        assertEquals("player-2", auction.highestBidderId)
        assertNull(auction.timerEndTime)

        // Assert: The card has NOT been given away yet (remains in limbo until resolved)
        updatedState.players.forEach { player ->
            assertTrue(player.animals.isEmpty())
        }
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
        val initialPlayers =
            listOf(
                PlayerState(id = "player-1", name = "Player 1", moneyCards = emptyList()),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    moneyCards = createDummyMoney("player-2", listOf(10, 10)),
                ),
                PlayerState(id = "player-3", name = "Player 3"),
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                auctionState = auctionState,
                players = initialPlayers,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: Auctioneer decides NOT to buy (auctioneerBuysCard = false)
        val updatedState = session.resolveAuction("player-1", auctioneerBuysCard = false)

        val seller = updatedState.players.find { it.id == "player-1" }!!
        val buyer = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Buyer gets the animal card, money transfers from buyer to seller
        assertTrue(buyer.animals.contains(targetCard))
        assertTrue(buyer.moneyCards.isEmpty())
        assertEquals(2, seller.moneyCards.size)
        assertEquals(20, seller.totalMoney())

        // Assert: State loops back to choice and index steps up
        assertNull(updatedState.auctionState)
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.currentPlayerIndex)
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
        val initialPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    moneyCards = createDummyMoney("player-1", listOf(50)),
                ),
                PlayerState(id = "player-2", name = "Player 2", moneyCards = emptyList()),
                PlayerState(id = "player-3", name = "Player 3"),
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                auctionState = auctionState,
                players = initialPlayers,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: Auctioneer buys the card (auctioneerBuysCard = true)
        val updatedState = session.resolveAuction("player-1", auctioneerBuysCard = true)

        val buyer =
            updatedState.players.find { it.id == "player-1" }!! // Auctioneer becomes receiver
        val seller = updatedState.players.find { it.id == "player-2" }!! // Bidder becomes seller

        // Assert: Auctioneer gets the card and gives their 50 card away (No change given!)
        assertTrue(buyer.animals.contains(targetCard))
        assertTrue(buyer.moneyCards.isEmpty())
        assertEquals(1, seller.moneyCards.size)
        assertEquals(50, seller.totalMoney())
    }

    @Test
    fun `resolveAuction fails if actor is not in room`() {
        val session =
            GameSession.fromState(
                "game-1",
                baselineState.copy(phase = GamePhase.AUCTION_RESOLUTION),
            )

        val exception =
            assertThrows<GameException> {
                session.resolveAuction("nonexistent player", auctioneerBuysCard = false)
            }
        assertEquals(GameErrorReason.UNKNOWN_ACTOR, exception.reason)
    }

    @Test
    fun `resolveAuction fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.resolveAuction("player-1", auctioneerBuysCard = false)
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `resolveAuction fails if actor is not the auctioneer`() {
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 10,
                highestBidderId = "player-2",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                auctionState = auctionState,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        val exception =
            assertThrows<GameException> {
                session.resolveAuction(
                    "player-2",
                    auctioneerBuysCard = false,
                ) // Player 2 tries to resolve, but Player 1 is auctioneer
            }
        assertEquals(GameErrorReason.NOT_AUCTIONEER, exception.reason)
    }

    @Test
    fun `resolveAuction fails if auctioneer buys but lacks enough money`() {
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                auctioneerId = "player-1",
                highestBid = 40,
                highestBidderId = "player-2",
            )
        // Auctioneer only has 10 money, but needs 40 to buy
        val poorAuctioneerPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    moneyCards = listOf(MoneyCard("p1-10", 10)),
                ),
                PlayerState(id = "player-2", name = "Player 2", moneyCards = emptyList()),
                PlayerState(id = "player-3", name = "Player 3"),
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                auctionState = auctionState,
                players = poorAuctioneerPlayers,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        val exception =
            assertThrows<GameException> {
                session.resolveAuction("player-1", auctioneerBuysCard = true)
            }
        assertEquals(GameErrorReason.NOT_ENOUGH_MONEY, exception.reason)
    }

    @Test
    fun `resolveAuction restarts auction and excludes bidder when winning bid was a bluff`() {
        val targetCard = AnimalCard("cow-1", AnimalType.COW)
        val auctionState =
            AuctionState(
                auctionCard = targetCard,
                auctioneerId = "player-1",
                highestBid = 20,
                highestBidderId = "player-2",
            )
        val bluffingBidderPlayers =
            listOf(
                PlayerState(id = "player-1", name = "Player 1", moneyCards = emptyList()),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    moneyCards = createDummyMoney("player-2", listOf(10)),
                ),
                PlayerState(id = "player-3", name = "Player 3", moneyCards = emptyList()),
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                auctionState = auctionState,
                players = bluffingBidderPlayers,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        val updatedState = session.resolveAuction("player-1", auctioneerBuysCard = false)

        assertEquals(GamePhase.AUCTION_BIDDING, updatedState.phase)
        val restartedAuction = updatedState.auctionState
        assertNotNull(restartedAuction)
        assertEquals(targetCard, restartedAuction.auctionCard)
        assertEquals(0, restartedAuction.highestBid)
        assertNull(restartedAuction.highestBidderId)
        assertTrue("player-2" in restartedAuction.excludedPlayerIds)
        assertNotNull(restartedAuction.timerEndTime)

        val event = updatedState.lastEvent
        assertTrue(event is GameEvent.BluffDetected)
        assertEquals("player-2", event.playerId)
    }

    @Test
    fun `chooseTrade creates trade state and transitions phase on happy path`() {
        // Setup: Player 1 initiates a trade with Player 2 for a COW. Both own a COW.
        val player1Money = createDummyMoney("player-1", listOf(10))

        println("DEBUG GENERATED ID: " + player1Money.first().id)

        val initialPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                    moneyCards = player1Money,
                ),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    animals = listOf(AnimalCard("c2", AnimalType.COW)),
                ),
                PlayerState(id = "player-3", name = "Player 3"),
            )
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players = initialPlayers,
                currentPlayerIndex = 0, // Player 1 is active
            )
        val session = GameSession.fromState("game-1", playableState)

        val targetCardId = "player-1-10-1"

        val updatedState =
            session.chooseTrade(
                actorId = "player-1",
                targetId = "player-2",
                animalType = AnimalType.COW,
                offeredMoneyCardIds = setOf(targetCardId),
            )

        // Assert: Phase transition
        assertEquals(GamePhase.TRADE_RESPONSE, updatedState.phase)

        // Assert: Trade details are initialized accurately
        val trade = updatedState.tradeState
        assertNotNull(trade)
        assertEquals("player-1", trade.initiatorId)
        assertEquals("player-2", trade.targetId)
        assertEquals(AnimalType.COW, trade.requestedAnimalType)
        assertEquals(setOf(targetCardId), trade.offeredMoneyCardIds)
        assertTrue(trade.counterOfferedMoneyCardIds.isEmpty())
    }

    @Test
    fun `chooseTrade fails if actor is not in room`() {
        val session =
            GameSession.fromState("game-1", baselineState.copy(phase = GamePhase.PLAYER_CHOICE))

        val exception =
            assertThrows<GameException> {
                session.chooseTrade(
                    "nonexistent player",
                    "player-2",
                    AnimalType.COW,
                    setOf("p1-10"),
                )
            }
        assertEquals(GameErrorReason.UNKNOWN_ACTOR, exception.reason)
    }

    @Test
    fun `chooseTrade fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.NOT_STARTED)
        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade("player-1", "player-2", AnimalType.COW, setOf("p1-10"))
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `chooseTrade fails if actor is not the active player`() {
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                currentPlayerIndex = 1, // Player 2's turn
            )
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade("player-1", "player-2", AnimalType.COW, setOf("p1-10"))
            }
        assertEquals(GameErrorReason.NOT_YOUR_TURN, exception.reason)
    }

    @Test
    fun `chooseTrade fails if trade target does not exist`() {
        val playableState =
            baselineState.copy(phase = GamePhase.PLAYER_CHOICE, currentPlayerIndex = 0)
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade("player-1", "fake-target-id", AnimalType.COW, setOf("p1-10"))
            }
        assertEquals(GameErrorReason.UNKNOWN_TRADE_TARGET, exception.reason)
    }

    @Test
    fun `chooseTrade fails if actor targets themselves`() {
        val playableState =
            baselineState.copy(phase = GamePhase.PLAYER_CHOICE, currentPlayerIndex = 0)
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade("player-1", "player-1", AnimalType.COW, setOf("p1-10"))
            }
        assertEquals(GameErrorReason.TARGETING_SELF, exception.reason)
    }

    @Test
    fun `chooseTrade fails if initiator does not own the requested animal type`() {
        // Player 1 has a PIG instead of a COW
        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("p1", AnimalType.PIG)),
                ),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                ),
            )
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players = customPlayers,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade("player-1", "player-2", AnimalType.COW, setOf("p1-10"))
            }
        assertEquals(GameErrorReason.INITIATOR_MISSING_ANIMAL, exception.reason)
    }

    @Test
    fun `chooseTrade fails if target does not own the requested animal type`() {
        // Player 2 (target) has an empty animal list
        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                ),
                PlayerState(id = "player-2", name = "Player 2", animals = emptyList()),
            )
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players = customPlayers,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade("player-1", "player-2", AnimalType.COW, setOf("p1-10"))
            }
        assertEquals(GameErrorReason.TARGET_MISSING_ANIMAL, exception.reason)
    }

    @Test
    fun `chooseTrade fails if money offer is empty`() {
        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                ),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    animals = listOf(AnimalCard("c2", AnimalType.COW)),
                ),
            )
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players = customPlayers,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade(
                    "player-1",
                    "player-2",
                    AnimalType.COW,
                    offeredMoneyCardIds = emptySet(),
                )
            }
        assertEquals(GameErrorReason.OFFER_EMPTY, exception.reason)
    }

    @Test
    fun `chooseTrade fails if initiator does not own the offered money cards`() {
        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                    moneyCards = createDummyMoney("player-1", listOf(10)),
                ),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    animals = listOf(AnimalCard("c2", AnimalType.COW)),
                ),
            )
        val playableState =
            baselineState.copy(
                phase = GamePhase.PLAYER_CHOICE,
                players = customPlayers,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", playableState)

        val exception =
            assertThrows<GameException> {
                session.chooseTrade(
                    "player-1",
                    "player-2",
                    AnimalType.COW,
                    offeredMoneyCardIds = setOf("fake-money-id"),
                )
            }
        assertEquals(GameErrorReason.NOT_OWNED_MONEY_CARDS, exception.reason)
    }

    @Test
    fun `respondToTrade processes blind acceptance path successfully`() {
        // Setup: Player 1 offers 10 money for a COW. Player 2 accepts blindly.
        val offerCardId = "player-1-10-1"

        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                offeredMoneyCardIds = setOf(offerCardId),
            )
        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                    moneyCards = createDummyMoney("player-1", listOf(10)),
                ),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    animals = listOf(AnimalCard("c2", AnimalType.COW)),
                    moneyCards = emptyList(),
                ),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players = customPlayers,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Player 2 counteroffers nothing (blind acceptance)
        val updatedState =
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = emptySet())

        val initiator = updatedState.players.find { it.id == "player-1" }!!
        val target = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Phase transitions, initiator gets the card, money changes hands
        assertEquals(GamePhase.TRADE_REVEAL, updatedState.phase)
        assertEquals(2, initiator.animals.size) // Got Player 2's cow
        assertTrue(target.animals.isEmpty())
        assertTrue(target.moneyCards.any { it.id == offerCardId })
        assertEquals(10, updatedState.tradeState?.offeredMoney)
        assertEquals(0, updatedState.tradeState?.counterOfferedMoney)
    }

    @Test
    fun `respondToTrade processes counteroffer where initiator wins the tiebreaker`() {
        // Rule check: initiator Total >= target Total means initiator wins on ties
        val initiatorCardId = "player-1-10-1"
        val targetCardId = "player-2-10-1"

        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                offeredMoneyCardIds = setOf(initiatorCardId),
            )
        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                    moneyCards = createDummyMoney("player-1", listOf(10)),
                ),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    animals = listOf(AnimalCard("c2", AnimalType.COW)),
                    moneyCards = createDummyMoney("player-2", listOf(10)),
                ),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players = customPlayers,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Player 2 counters with 10 money
        val updatedState =
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = setOf(targetCardId))

        val initiator = updatedState.players.find { it.id == "player-1" }!!
        val target = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Initiator wins the tiebreaker and takes the card. Both kept both sets of cash.
        assertEquals(2, initiator.animals.size)
        assertTrue(target.animals.isEmpty())
        assertTrue(initiator.moneyCards.any { it.id == targetCardId })
        assertTrue(target.moneyCards.any { it.id == initiatorCardId })
    }

    @Test
    fun `respondToTrade processes counteroffer where target wins with higher value`() {
        val initiatorCardId = "player-1-10-1"
        val targetCardId = "player-2-50-1"

        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                offeredMoneyCardIds = setOf(initiatorCardId),
            )
        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    animals = listOf(AnimalCard("c1", AnimalType.COW)),
                    moneyCards = createDummyMoney("player-1", listOf(10)),
                ),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    animals = listOf(AnimalCard("c2", AnimalType.COW)),
                    moneyCards = createDummyMoney("player-2", listOf(50)),
                ),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players = customPlayers,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Player 2 counters with a 50 card
        val updatedState =
            session.respondToTrade("player-2", counterOfferedMoneyCardIds = setOf(targetCardId))

        val initiator = updatedState.players.find { it.id == "player-1" }!!
        val target = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Target wins (50 > 10) and takes the initiator's card
        assertTrue(initiator.animals.isEmpty())
        assertEquals(2, target.animals.size)
        assertEquals(50, updatedState.tradeState?.counterOfferedMoney)
    }

    @Test
    fun `respondToTrade fails if actor is not in room`() {
        val session =
            GameSession.fromState("game-1", baselineState.copy(phase = GamePhase.TRADE_RESPONSE))

        val exception =
            assertThrows<GameException> {
                session.respondToTrade(
                    "nonexistent player",
                    counterOfferedMoneyCardIds = emptySet(),
                )
            }
        assertEquals(GameErrorReason.UNKNOWN_ACTOR, exception.reason)
    }

    @Test
    fun `respondToTrade fails if phase is invalid`() {
        val brokenState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        val session = GameSession.fromState("game-1", brokenState)

        val exception =
            assertThrows<GameException> {
                session.respondToTrade("player-2", counterOfferedMoneyCardIds = emptySet())
            }
        assertEquals(GameErrorReason.INVALID_PHASE, exception.reason)
    }

    @Test
    fun `respondToTrade fails if actor is not the designated trade target`() {
        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
            )
        val activeState =
            baselineState.copy(phase = GamePhase.TRADE_RESPONSE, tradeState = tradeState)
        val session = GameSession.fromState("game-1", activeState)

        val exception =
            assertThrows<GameException> {
                session.respondToTrade(
                    "player-3",
                    counterOfferedMoneyCardIds = emptySet(),
                ) // Player 3 is trying to respond to Player 2's trade
            }
        assertEquals(GameErrorReason.NOT_TRADE_TARGET, exception.reason)
    }

    @Test
    fun `respondToTrade fails if target does not own the counter offered money cards`() {
        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
            )
        val customPlayers =
            listOf(
                PlayerState(id = "player-1", name = "Player 1"),
                PlayerState(
                    id = "player-2",
                    name = "Player 2",
                    moneyCards = createDummyMoney("player-2", listOf(10)),
                ),
            )
        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players = customPlayers,
            )
        val session = GameSession.fromState("game-1", activeState)

        val exception =
            assertThrows<GameException> {
                session.respondToTrade(
                    "player-2",
                    counterOfferedMoneyCardIds = setOf("fake-money-id"),
                )
            }
        assertEquals(GameErrorReason.NOT_OWNED_MONEY_CARDS, exception.reason)
    }

    @Test
    fun `endTradeReveal clears trade state and advances turn on happy path`() {
        // Setup: Game is in the TRADE_REVEAL phase with an active trade state
        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
            )
        val activeRevealState =
            baselineState.copy(
                phase = GamePhase.TRADE_REVEAL,
                tradeState = tradeState,
                currentPlayerIndex = 0, // It was Player 1's turn
                roundNumber = 5,
            )
        val session = GameSession.fromState("game-1", activeRevealState)

        val updatedState = session.endTradeReveal()

        // Assert: Trade state is cleared
        assertNull(updatedState.tradeState)

        // Assert: Phase goes back to PLAYER_CHOICE and turn advances to the next player
        assertEquals(GamePhase.PLAYER_CHOICE, updatedState.phase)
        assertEquals(1, updatedState.currentPlayerIndex) // Moved to Player 2
        assertEquals(6, updatedState.roundNumber) // Round incremented
    }

    @Test
    fun `endTradeReveal throws IllegalStateException if phase is not TRADE_REVEAL`() {
        // Setup: Game is in an invalid phase for this action (e.g., PLAYER_CHOICE)
        val invalidPhaseState = baselineState.copy(phase = GamePhase.PLAYER_CHOICE)
        val session = GameSession.fromState("game-1", invalidPhaseState)

        // Assert: check() throws an IllegalStateException
        assertThrows<IllegalStateException> {
            session.endTradeReveal()
        }
    }

    @Test
    fun `chooseAuction handles donkey bonus`() {
        // Setup: Donkey is on top of the deck. All players get money.
        val donkey = AnimalCard("donkey-1", AnimalType.DONKEY)
        // Ensure there are 3 other donkeys in the deck to make this the 1st donkey (CARDS_PER_ANIMAL_TYPE = 4)
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

        val updatedState = session.chooseAuction("player-1")

        // Assert: All players got 50 bonus
        updatedState.players.forEach { player ->
            assertTrue(player.moneyCards.any { it.value == 50 && it.id.contains("donkey-1") })
        }
        assertNotNull(updatedState.lastEvent)
        assertTrue(updatedState.lastEvent is GameEvent.MoneyBonus)
        assertEquals(50, (updatedState.lastEvent as GameEvent.MoneyBonus).amount)
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

        // Target: Resolve an auction where player 1 gets the 4th card of the last animal type
        val lastCard = AnimalCard("${lastAnimalType.name}-4", lastAnimalType)
        val player1With3Cards =
            playersWithQuartets[0].copy(
                animals =
                    playersWithQuartets[0].animals +
                        (1..3).map { AnimalCard("${lastAnimalType.name}-$it", lastAnimalType) },
            )
        val finalPlayers = listOf(player1With3Cards) + playersWithQuartets.drop(1)

        val auctionState =
            AuctionState(
                auctionCard = lastCard,
                auctioneerId = "player-1",
                highestBid = 0,
                highestBidderId = null,
            )

        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_BIDDING,
                players = finalPlayers,
                auctionState = auctionState,
                currentPlayerIndex = 0,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: Close auction (no bids, auctioneer gets it)
        val intermediateState = session.closeAuctionAfterTimeout()

        // Assert: Transitions to resolution first
        assertEquals(GamePhase.AUCTION_RESOLUTION, intermediateState.phase)

        // Act: Resolve auction
        val updatedState = session.resolveAuction("player-1", true)

        // Assert: Game is finished
        assertEquals(GamePhase.FINISHED, updatedState.phase)
        assertNull(updatedState.lastEvent)
    }

    @Test
    fun `selectMoneyCardsForPayment chooses optimal overpayment`() {
        // Setup: Player has 50 and 10. Needs to pay 15. Optimal is 50?
        // Actually, no change is given, so if they have 10 and 10, they pay 20.
        // If they have 50 and 10, they pay 50?
        // Let's test: 10, 10, 50. Need to pay 15. Optimal is 10+10=20.
        val initialPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "Player 1",
                    moneyCards = createDummyMoney("player-1", listOf(50, 10, 10)),
                ),
                PlayerState(id = "player-2", name = "Player 2", moneyCards = emptyList()),
                PlayerState(id = "player-3", name = "Player 3"),
            )
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard("c1", AnimalType.COW),
                auctioneerId = "player-2",
                highestBid = 15,
                highestBidderId = "player-1",
            )
        val resolutionState =
            baselineState.copy(
                phase = GamePhase.AUCTION_RESOLUTION,
                players = initialPlayers,
                auctionState = auctionState,
            )
        val session = GameSession.fromState("game-1", resolutionState)

        // Act: resolve auction
        val updatedState = session.resolveAuction("player-2", auctioneerBuysCard = false)

        val buyer = updatedState.players.find { it.id == "player-1" }!!
        val seller = updatedState.players.find { it.id == "player-2" }!!

        // Assert: Buyer paid 20 (two 10s) instead of 50.
        assertEquals(1, buyer.moneyCards.size)
        assertEquals(50, buyer.totalMoney())
        assertEquals(2, seller.moneyCards.size)
        assertEquals(20, seller.totalMoney())
    }

    @Test
    fun `moveAnimalType moves two cards when both players have multiple`() {
        // Setup: Player 1 has 3 cows, Player 2 has 2 cows. Trade results in moving 2 cows.
        val p1Cows = (1..3).map { AnimalCard("p1-c$it", AnimalType.COW) }
        val p2Cows = (1..2).map { AnimalCard("p2-c$it", AnimalType.COW) }

        val tradeState =
            TradeState(
                initiatorId = "player-1",
                targetId = "player-2",
                requestedAnimalType = AnimalType.COW,
                offeredMoneyCardIds = setOf("player-1-10-1"),
            )

        val customPlayers =
            listOf(
                PlayerState(
                    id = "player-1",
                    name = "P1",
                    animals = p1Cows,
                    moneyCards =
                        createDummyMoney("player-1", listOf(10)),
                ),
                PlayerState(
                    id = "player-2",
                    name = "P2",
                    animals = p2Cows,
                    moneyCards = emptyList(),
                ),
                PlayerState(id = "player-3", name = "P3"),
            )

        val activeState =
            baselineState.copy(
                phase = GamePhase.TRADE_RESPONSE,
                tradeState = tradeState,
                players = customPlayers,
            )
        val session = GameSession.fromState("game-1", activeState)

        // Act: Blind acceptance
        val updatedState = session.respondToTrade("player-2", emptySet())

        val initiator = updatedState.players.find { it.id == "player-1" }!!
        val target = updatedState.players.find { it.id == "player-2" }!!

        // Assert: 2 cards were moved. Initiator has 3 + 2 = 5 cows?
        // Wait, the logic is: animalCardsToMoveCount = if (fromCount >= 2 && toCount >= 2) 2 else 1
        // fromCount = target's cows = 2. toCount = initiator's cows = 3. Both >= 2, so move 2.
        assertEquals(5, initiator.animals.size)
        assertEquals(0, target.animals.size)
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
