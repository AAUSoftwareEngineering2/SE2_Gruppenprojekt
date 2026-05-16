package at.aau.kuhhandel.server.model

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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionTest {
    private companion object {
        const val CARDS_PER_ANIMAL_TYPE = 4
        val FULL_DECK_SIZE = AnimalType.entries.size * CARDS_PER_ANIMAL_TYPE
        val STARTING_MONEY_VALUES = listOf(0, 0, 10, 10, 10, 10, 50)
    }

    @Test
    fun test_newSession_initializesCorrectly() {
        val session = GameSession("12345", "player-1", "Player 1")

        assertEquals("12345", session.gameId)
        assertEquals(GamePhase.NOT_STARTED, session.state.phase)
        assertTrue(session.state.deck.isEmpty())
        assertNull(session.state.currentFaceUpCard)
        assertEquals(0, session.state.currentPlayerIndex)
        assertEquals(1, session.state.players.size)
        assertEquals(
            "player-1",
            session.state.players[0]
                .id,
        )
        assertEquals("player-1", session.state.hostPlayerId)
        assertNull(session.state.auctionState)
        assertNull(session.state.tradeState)
    }

    @Test
    fun test_startGame_initializesPlayerTurnAndRoundOne() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")

        session.startGame()

        assertEquals(GamePhase.PLAYER_TURN, session.state.phase)
        assertEquals(1, session.state.roundNumber)
        assertEquals(FULL_DECK_SIZE, session.state.deck.size())
        assertNull(session.state.currentFaceUpCard)
        assertEquals(0, session.state.currentPlayerIndex)
        assertEquals(3, session.state.players.size)
        assertNull(session.state.auctionState)
        assertNull(session.state.tradeState)
    }

    @Test
    fun test_startGame_createsFullAnimalDeck() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")

        session.startGame()

        AnimalType.entries.forEach { type ->
            assertEquals(
                CARDS_PER_ANIMAL_TYPE,
                session.state.deck.cards
                    .count { card -> card.type == type },
            )
        }
        assertEquals(
            FULL_DECK_SIZE,
            session.state.deck.cards
                .map { card -> card.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun test_startGame_dealsStartingMoneyToEveryPlayer() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")

        session.startGame()

        session.state.players.forEach { player ->
            assertEquals(STARTING_MONEY_VALUES, player.moneyCards.map { card -> card.value })
            assertEquals(90, player.totalMoney())
            assertEquals(emptyList(), player.animals)
        }
        assertEquals(
            session.state.players.sumOf { player -> player.moneyCards.size },
            session.state.players
                .flatMap { player -> player.moneyCards }
                .map { card -> card.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun test_startGame_rejectsWithInsufficientPlayers() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")

        assertFailsWith<IllegalStateException> {
            session.startGame()
        }
    }

    @Test
    fun test_startGame_rejectsAlreadyStartedGame() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.startGame()
        }
    }

    @Test
    fun test_addPlayer_addsPlayerAndDoesNotChangeHost() {
        val session = GameSession("12345", "player-1", "Player 1")

        session.addPlayer("player-2", "Player 2")

        assertEquals(
            listOf(
                PlayerState("player-1", "Player 1"),
                PlayerState("player-2", "Player 2"),
            ),
            session.state.players,
        )
        assertEquals("player-1", session.state.hostPlayerId)
    }

    @Test
    fun test_addPlayer_rejectsWrongPhase() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.addPlayer("player-4", "Player 4")
        }
    }

    @Test
    fun test_addPlayer_rejectsExistingId() {
        val session = GameSession("12345", "player-1", "Player 1")

        assertFailsWith<IllegalStateException> {
            session.addPlayer("player-1", "Player with same ID")
        }
    }

    @Test
    fun test_addPlayer_rejectsWhenFull() {
        val session = GameSession("12345", "player-1", "Player 1")
        for (i in 2..5) {
            session.addPlayer("player-$i", "Player $i")
        }
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.addPlayer("player-6", "Player 6")
        }
    }

    @Test
    fun test_removePlayer_removesNonHostAndDoesNotChangeHost() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")

        session.removePlayer("player-2")

        assertEquals(
            listOf(
                PlayerState("player-1", "Player 1"),
            ),
            session.state.players,
        )
        assertEquals("player-1", session.state.hostPlayerId)
    }

    @Test
    fun test_removePlayer_removesHostAndUpdatesHost() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")

        session.removePlayer("player-1")

        assertEquals(
            listOf(
                PlayerState("player-2", "Player 2"),
            ),
            session.state.players,
        )
        assertEquals("player-2", session.state.hostPlayerId)
    }

    @Test
    fun test_removePlayer_removesLastPlayerAndResetsHost() {
        val session = GameSession("12345", "player-1", "Player 1")

        session.removePlayer("player-1")

        assertTrue(
            session.state.players.isEmpty(),
        )
        assertNull(session.state.hostPlayerId)
    }

    @Test
    fun test_removePlayer_rejectsWrongPhase() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.removePlayer("player-1")
        }
    }

    @Test
    fun test_removePlayer_rejectsNonexistentId() {
        val session = GameSession("12345", "player-1", "Player 1")

        assertFailsWith<IllegalStateException> {
            session.removePlayer("player-2")
        }
    }

    @Test
    fun test_revealNextCard_drawsTopCardDuringPlayerTurn() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        val state = session.revealNextCard()

        assertNotNull(state.currentFaceUpCard)
        assertEquals(FULL_DECK_SIZE - 1, state.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_revealNextCard_updatesStoredState() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        session.revealNextCard()

        assertNotNull(session.state.currentFaceUpCard)
        assertEquals(FULL_DECK_SIZE - 1, session.state.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, session.state.phase)
    }

    @Test
    fun test_revealNextCard_lastCardDoesNotImmediatelyFinishGame() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        repeat(FULL_DECK_SIZE - 1) {
            session.revealNextCard()
        }
        val stateAfterLastCard = session.revealNextCard()

        assertNotNull(stateAfterLastCard.currentFaceUpCard)
        assertEquals(0, stateAfterLastCard.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, stateAfterLastCard.phase)
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckAlreadyEmpty() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        repeat(FULL_DECK_SIZE) {
            session.revealNextCard()
        }
        val finalState = session.revealNextCard()

        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
        assertNull(finalState.auctionState)
        assertNull(finalState.tradeState)
    }

    @Test
    fun test_revealCard_rejectsWrongPhase() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")

        assertFailsWith<IllegalStateException> {
            session.revealNextCard()
        }
    }

    @Test
    fun test_chooseAuction_drawsTopCardAndMovesPhaseToAuction() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                roundNumber = 1,
                players = listOf(player("player-1")),
                deck =
                    AnimalDeck(
                        listOf(
                            AnimalCard(id = "1", type = AnimalType.COW),
                            AnimalCard(id = "2", type = AnimalType.DOG),
                        ),
                    ),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.chooseAuction()

        assertEquals(GamePhase.AUCTION, updatedState.phase)
        val auctionState = updatedState.auctionState
        assertEquals(AnimalCard(id = "2", type = AnimalType.DOG), auctionState?.auctionCard)
        assertEquals("player-1", auctionState?.auctioneerId)
        assert(auctionState?.timerEndTime != null)
        assertEquals(1, updatedState.deck.size())
        assertNull(updatedState.currentFaceUpCard)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_chooseAuction_updatesStoredState() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        val state = session.chooseAuction()

        assertEquals(GamePhase.AUCTION, state.phase)
        assertNotNull(state.auctionState)
        assertEquals(FULL_DECK_SIZE - 1, state.deck.size())
        assertNull(state.currentFaceUpCard)
        assertEquals(GamePhase.AUCTION, session.state.phase)
    }

    @Test
    fun test_chooseAuction_rejectsWrongPhase() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")

        assertFailsWith<IllegalStateException> {
            session.revealNextCard()
        }
    }

    @Test
    fun test_chooseAuction_rejectsEmptyDeck() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players = listOf(player("player-1")),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.chooseAuction()
        }
    }

    @Test
    fun test_placeBid_updatesHighestBidAndBidder() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()
        session.chooseAuction()

        session.placeBid("player-2", 10)

        assertEquals(GamePhase.AUCTION, session.state.phase)
        assertEquals(10, session.state.auctionState?.highestBid)
        assertEquals("player-2", session.state.auctionState?.highestBidderId)
    }

    @Test
    fun test_placeBid_rejectsUnknownBidder() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()
        session.chooseAuction()

        assertFailsWith<IllegalArgumentException> {
            session.placeBid("player-4", 10)
        }
    }

    @Test
    fun test_placeBid_rejectsWrongPhase() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.placeBid("player-2", 10)
        }
    }

    @Test
    fun test_placeBid_rejectsAuctioneer() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()
        session.chooseAuction()

        assertFailsWith<IllegalArgumentException> {
            session.placeBid("player-1", 10)
        }
    }

    @Test
    fun test_placeBid_rejectsLowerOrEqualBid() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()
        session.chooseAuction()
        session.placeBid("player-2", 10)

        assertFailsWith<IllegalArgumentException> {
            session.placeBid("player-3", 10)
        }
    }

    @Test
    fun test_placeBid_rejectsMissingAuctionState() {
        val state =
            GameState(
                phase = GamePhase.AUCTION,
                players = listOf(player("player-1"), player("player-2")),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.placeBid(bidderId = "player-2", amount = 10)
        }
    }

    @Test
    fun test_placeBid_rejectsClosedAuction() {
        val state =
            activeAuctionState(
                auctionState =
                    auctionFixture(
                        highestBid = 10,
                        highestBidderId = "player-2",
                        isClosed = true,
                    ),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.placeBid(bidderId = "player-3", amount = 20)
        }
    }

    @Test
    fun test_placeBid_rejectsBidderWithoutEnoughMoney() {
        val state =
            activeAuctionState(
                bidderMoneyCards = listOf(MoneyCard(id = "p2-money-10", value = 10)),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.placeBid(bidderId = "player-2", amount = 20)
        }
    }

    @Test
    fun test_closeAuction_marksAuctionClosed() {
        val state = activeAuctionState()
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.closeAuction()

        assertEquals(true, updatedState.auctionState?.isClosed)
    }

    @Test
    fun test_closeAuction_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.closeAuction()
        }
    }

    @Test
    fun test_closeAuction_rejectsMissingAuctionState() {
        val state =
            GameState(
                phase = GamePhase.AUCTION,
                players = listOf(player("player-1"), player("player-2")),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.closeAuction()
        }
    }

    @Test
    fun test_closeAuction_rejectsAlreadyClosedAuction() {
        val state =
            activeAuctionState(
                auctionFixture(isClosed = true),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.closeAuction()
        }
    }

    @Test
    fun test_resolveAuction_sellsCardToHighestBidder() {
        val state =
            activeAuctionState(
                auctionFixture(
                    highestBid = 10,
                    highestBidderId = "player-2",
                    isClosed = true,
                ),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.resolveAuction(auctioneerBuysCard = false)

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertEquals(
            listOf(AnimalCard(id = "auction-card", type = AnimalType.COW)),
            updatedState.players[1].animals,
        )
        assertEquals(listOf("p2-money-50"), updatedState.players[1].moneyCards.map { it.id })
        assertEquals(
            listOf("p1-money-10", "p1-money-50", "p2-money-10"),
            updatedState.players[0].moneyCards.map { it.id },
        )
        assertNull(updatedState.auctionState)
    }

    @Test
    fun test_resolveAuction_allowsAuctioneerToBuyCard() {
        val state =
            activeAuctionState(
                auctionFixture(
                    highestBid = 10,
                    highestBidderId = "player-2",
                    isClosed = true,
                ),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.resolveAuction(auctioneerBuysCard = true)

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertEquals(
            listOf(AnimalCard(id = "auction-card", type = AnimalType.COW)),
            updatedState.players[0].animals,
        )
        assertEquals(listOf("p1-money-50"), updatedState.players[0].moneyCards.map { it.id })
        assertEquals(
            listOf("p2-money-10", "p2-money-50", "p1-money-10"),
            updatedState.players[1].moneyCards.map { it.id },
        )
        assertNull(updatedState.auctionState)
    }

    @Test
    fun test_resolveAuction_withoutBidGivesCardToAuctioneer() {
        val state = activeAuctionState(auctionFixture(isClosed = true))
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.resolveAuction(auctioneerBuysCard = false)

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertEquals(
            listOf(AnimalCard(id = "auction-card", type = AnimalType.COW)),
            updatedState.players[0].animals,
        )
        assertNull(updatedState.auctionState)
    }

    @Test
    fun test_resolveAuction_updatesStoredState() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()
        session.chooseAuction()
        session.closeAuction()

        val state = session.resolveAuction(auctioneerBuysCard = false)

        assertEquals(GamePhase.ROUND_END, state.phase)
        assertEquals(1, state.players[0].animals.size)
        assertNull(state.auctionState)
        assertEquals(state, session.state)
    }

    @Test
    fun test_resolveAuction_rejectsBuyBackWhenAuctioneerCannotPayHighestBidder() {
        val state =
            activeAuctionState(
                auctionState =
                    auctionFixture(
                        highestBid = 10,
                        highestBidderId = "player-2",
                        isClosed = true,
                    ),
                auctioneerMoneyCards = emptyList(),
                bidderMoneyCards = listOf(MoneyCard(id = "p2-money-10", value = 10)),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.resolveAuction(auctioneerBuysCard = true)
        }
    }

    @Test
    fun test_resolveAuction_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.resolveAuction(auctioneerBuysCard = false)
        }
    }

    @Test
    fun test_resolveAuction_rejectsOpenAuction() {
        val state = activeAuctionState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.resolveAuction(auctioneerBuysCard = false)
        }
    }

    @Test
    fun test_chooseTrade_movesPhaseToTrade() {
        val cow = AnimalCard(id = "cow-1", type = AnimalType.COW)
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                roundNumber = 1,
                players =
                    listOf(
                        player(id = "player-1", animals = listOf(cow)),
                        player(
                            id = "player-2",
                            animals = listOf(AnimalCard(id = "cow-2", type = AnimalType.COW)),
                        ),
                    ),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.chooseTrade(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
            )

        assertEquals(GamePhase.TRADE, updatedState.phase)
        assertEquals(
            TradeState(
                initiatingPlayerId = "player-1",
                challengedPlayerId = "player-2",
                requestedAnimalType = AnimalType.COW,
            ),
            updatedState.tradeState,
        )
        assertNull(updatedState.auctionState)
    }

    @Test
    fun test_chooseTrade_storesOfferedMoneyCards() {
        val state =
            tradeReadyState(
                initiatingMoneyCards =
                    listOf(
                        MoneyCard(id = "money-10", value = 10),
                        MoneyCard(id = "money-50", value = 50),
                    ),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.chooseTrade(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
                offeredMoneyCardIds = listOf("money-10", "money-50"),
            )

        assertEquals(60, updatedState.tradeState?.offeredMoney)
        assertEquals(listOf("money-10", "money-50"), updatedState.tradeState?.offeredMoneyCardIds)
        assertEquals(2, updatedState.tradeState?.offeredMoneyCardCount)
        assertEquals(TradeStep.WAITING_FOR_RESPONSE, updatedState.tradeState?.step)
    }

    @Test
    fun test_chooseTrade_rejectsUnknownOfferedMoneyCard() {
        val state = tradeReadyState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.chooseTrade(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
                offeredMoneyCardIds = listOf("missing-card"),
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsUnknownPlayer() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        assertFailsWith<IllegalArgumentException> {
            session.chooseTrade("player-4", AnimalType.COW)
        }
    }

    @Test
    fun test_chooseTrade_rejectsSelfChallenge() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players = listOf(player("player-1")),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.chooseTrade(
                challengedPlayerId = "player-1",
                animalType = AnimalType.COW,
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.AUCTION)
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.chooseTrade(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsMissingActivePlayer() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                currentPlayerIndex = 1,
                players = listOf(player("player-1")),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.chooseTrade(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsUnknownChallengedPlayer() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players = listOf(player("player-1")),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.chooseTrade(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsPlayersWithoutSharedAnimalType() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players =
                    listOf(
                        player(
                            id = "player-1",
                            animals = listOf(AnimalCard(id = "cow-1", type = AnimalType.COW)),
                        ),
                        player(
                            id = "player-2",
                            animals = listOf(AnimalCard(id = "dog-1", type = AnimalType.DOG)),
                        ),
                    ),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.chooseTrade(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
            )
        }
    }

    @Test
    fun test_offerTrade_setsOfferedMoneyAndCardsOnTradeState() {
        val state = tradeState()
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.offerTrade(offeredMoneyCardIds = listOf("m-10", "m-50"))

        assertEquals(GamePhase.TRADE, updatedState.phase)
        assertEquals(60, updatedState.tradeState?.offeredMoney)
        assertEquals(listOf("m-10", "m-50"), updatedState.tradeState?.offeredMoneyCardIds)
    }

    @Test
    fun test_offerTrade_rejectsWrongPhase() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.offerTrade(listOf("m-1"))
        }
    }

    @Test
    fun test_offerTrade_rejectsEmptyOffer() {
        val state = tradeState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.offerTrade(offeredMoneyCardIds = emptyList())
        }
    }

    @Test
    fun test_offerTrade_rejectsUnknownMoneyCardId() {
        val state = tradeState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.offerTrade(offeredMoneyCardIds = listOf("does-not-exist"))
        }
    }

    @Test
    fun test_offerTrade_rejectsWhenTradeStateIsMissing() {
        val state =
            GameState(
                phase = GamePhase.TRADE,
                players =
                    listOf(
                        PlayerState(
                            id = "player-1",
                            name = "player-1",
                            moneyCards = listOf(MoneyCard(id = "m-1", value = 10)),
                        ),
                    ),
                tradeState = null,
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.offerTrade(offeredMoneyCardIds = listOf("m-1"))
        }
    }

    @Test
    fun test_respondToTrade_acceptOfferMovesAnimalToInitiatingPlayerAndMoneyToChallengedPlayer() {
        val state =
            activeTradeState(
                initiatingMoneyCards = listOf(MoneyCard(id = "money-10", value = 10)),
                offeredMoneyCardIds = listOf("money-10"),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = true,
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertEquals(listOf("cow-1", "cow-2"), updatedState.players[0].animals.map { it.id })
        assertEquals(emptyList(), updatedState.players[1].animals)
        assertEquals(emptyList(), updatedState.players[0].moneyCards)
        assertEquals(listOf("money-10"), updatedState.players[1].moneyCards.map { it.id })
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_respondToTrade_acceptOfferIgnoresCounterOfferCards() {
        val state =
            activeTradeState(
                initiatingMoneyCards = listOf(MoneyCard(id = "money-10", value = 10)),
                challengedMoneyCards = listOf(MoneyCard(id = "money-50", value = 50)),
                offeredMoneyCardIds = listOf("money-10"),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = true,
                counterOfferedMoneyCardIds = listOf("money-50"),
            )

        assertEquals(listOf("cow-1", "cow-2"), updatedState.players[0].animals.map { it.id })
        assertEquals(
            listOf("money-50", "money-10"),
            updatedState.players[1].moneyCards.map { it.id },
        )
    }

    @Test
    fun test_respondToTrade_counterOfferHigherMovesAnimalToChallengedPlayerAndSwapsMoney() {
        val state =
            activeTradeState(
                initiatingMoneyCards = listOf(MoneyCard(id = "money-10", value = 10)),
                challengedMoneyCards = listOf(MoneyCard(id = "money-50", value = 50)),
                offeredMoneyCardIds = listOf("money-10"),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = false,
                counterOfferedMoneyCardIds = listOf("money-50"),
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertEquals(emptyList(), updatedState.players[0].animals)
        assertEquals(listOf("cow-2", "cow-1"), updatedState.players[1].animals.map { it.id })
        assertEquals(listOf("money-50"), updatedState.players[0].moneyCards.map { it.id })
        assertEquals(listOf("money-10"), updatedState.players[1].moneyCards.map { it.id })
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_respondToTrade_equalCounterOfferLetsInitiatingPlayerWin() {
        val state =
            activeTradeState(
                initiatingMoneyCards = listOf(MoneyCard(id = "money-a", value = 10)),
                challengedMoneyCards = listOf(MoneyCard(id = "money-b", value = 10)),
                offeredMoneyCardIds = listOf("money-a"),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = false,
                counterOfferedMoneyCardIds = listOf("money-b"),
            )

        assertEquals(listOf("cow-1", "cow-2"), updatedState.players[0].animals.map { it.id })
        assertEquals(emptyList(), updatedState.players[1].animals)
        assertEquals(listOf("money-b"), updatedState.players[0].moneyCards.map { it.id })
        assertEquals(listOf("money-a"), updatedState.players[1].moneyCards.map { it.id })
    }

    @Test
    fun test_respondToTrade_rejectsWrongPhaseWithoutOffer() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.respondToTrade("player-2", true)
        }
    }

    @Test
    fun test_respondToTrade_rejectsWrongResponder() {
        val state = activeTradeState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.respondToTrade(
                respondingPlayerId = "player-1",
                acceptsOffer = true,
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsUnknownCounterOfferCard() {
        val state = activeTradeState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = false,
                counterOfferedMoneyCardIds = listOf("missing-card"),
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsResolvedTradeState() {
        val state =
            activeTradeState().copy(
                tradeState =
                    requireNotNull(activeTradeState().tradeState).copy(
                        step = TradeStep.RESOLVED,
                        isResolved = true,
                    ),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = true,
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsMissingAnimalAtResolution() {
        val state =
            activeTradeState().copy(
                players =
                    listOf(
                        player(
                            id = "player-1",
                            animals = listOf(AnimalCard(id = "cow-1", type = AnimalType.COW)),
                        ),
                        player(id = "player-2"),
                    ),
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = true,
            )
        }
    }

    @Test
    fun test_respondToTrade_acceptTransfersAnimalAndMoney() {
        val state =
            tradeState(
                offeredMoneyCardIds = listOf("m-10", "m-50"),
                offeredMoney = 60,
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = true,
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertNull(updatedState.tradeState)

        val initiator = updatedState.players.first { it.id == "player-1" }
        val challenged = updatedState.players.first { it.id == "player-2" }

        // Initiator receives all matching COW cards from challenged player.
        assertEquals(3, initiator.animals.count { it.type == AnimalType.COW })
        assertEquals(0, challenged.animals.count { it.type == AnimalType.COW })

        // Money cards moved from initiator to challenged
        val transferredIds = listOf("m-10", "m-50")
        assertTrue(initiator.moneyCards.none { it.id in transferredIds })
        val transferredSum =
            challenged.moneyCards
                .filter { it.id in transferredIds }
                .sumOf { it.value }
        assertEquals(60, transferredSum)
    }

    @Test
    fun test_respondToTrade_rejectMovesToRoundEndWithoutTransfer() {
        val state =
            tradeState(
                offeredMoneyCardIds = listOf("m-10", "m-50"),
                offeredMoney = 60,
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = false,
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertNull(updatedState.tradeState)

        // No transfer happened
        val initiator = updatedState.players.first { it.id == "player-1" }
        val challenged = updatedState.players.first { it.id == "player-2" }
        assertEquals(1, initiator.animals.count { it.type == AnimalType.COW })
        assertEquals(2, challenged.animals.count { it.type == AnimalType.COW })
        assertEquals(2, initiator.moneyCards.size)
    }

    @Test
    fun test_respondToTrade_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.respondToTrade(respondingPlayerId = "player-2", acceptsOffer = true)
        }
    }

    @Test
    fun test_respondToTrade_rejectsNonChallengedResponder() {
        val state =
            tradeState(
                offeredMoneyCardIds = listOf("m-10"),
                offeredMoney = 10,
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.respondToTrade(respondingPlayerId = "player-1", acceptsOffer = true)
        }
    }

    @Test
    fun test_respondToTrade_rejectsAcceptanceWithoutPriorOffer() {
        val state = tradeState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.respondToTrade(respondingPlayerId = "player-2", acceptsOffer = true)
        }
    }

    @Test
    fun test_respondToTrade_rejectsWhenTradeStateIsMissing() {
        val state =
            GameState(
                phase = GamePhase.TRADE,
                players = listOf(PlayerState(id = "player-1", name = "player-1")),
                tradeState = null,
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.respondToTrade(respondingPlayerId = "player-2", acceptsOffer = false)
        }
    }

    @Test
    fun test_respondToTrade_rejectsIfChallengedNoLongerOwnsAnimalType() {
        val baseState =
            tradeState(
                offeredMoneyCardIds = listOf("m-10"),
                offeredMoney = 10,
            )
        // Strip the cow off the challenged player to simulate a state inconsistency.
        val state =
            baseState.copy(
                players =
                    baseState.players.map { p ->
                        if (p.id == "player-2") p.copy(animals = emptyList()) else p
                    },
            )
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalArgumentException> {
            session.respondToTrade(respondingPlayerId = "player-2", acceptsOffer = true)
        }
    }

    @Test
    fun test_respondToTrade_noAcceptanceNoCounterOfferMovesToRoundEnd() {
        val state =
            activeTradeState(
                initiatingMoneyCards = listOf(MoneyCard(id = "m-10", value = 10)),
                offeredMoneyCardIds = listOf("m-10"),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = false,
                counterOfferedMoneyCardIds = emptyList(),
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_finishRound_updatesStoredState() {
        val session = GameSession("12345", "player-1", "Player 1")
        session.addPlayer("player-2", "Player 2")
        session.addPlayer("player-3", "Player 3")
        session.startGame()
        session.chooseAuction()
        session.closeAuction()
        session.resolveAuction(auctioneerBuysCard = false)

        val state = session.finishRound()

        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.roundNumber)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
        assertEquals(state, session.state)
    }

    @Test
    fun test_finishRound_advancesToNextPlayerAndClearsRoundState() {
        val state =
            GameState(
                phase = GamePhase.ROUND_END,
                roundNumber = 1,
                players = listOf(player("player-1"), player("player-2")),
                currentPlayerIndex = 0,
                deck = AnimalDeck(listOf(AnimalCard(id = "1", type = AnimalType.COW))),
                currentFaceUpCard = AnimalCard(id = "2", type = AnimalType.DOG),
                auctionState = auctionFixture(AnimalCard(id = "3", type = AnimalType.CAT)),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.finishRound()

        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
        assertEquals(2, updatedState.roundNumber)
        assertEquals(1, updatedState.currentPlayerIndex)
        assertEquals("player-2", updatedState.players[updatedState.currentPlayerIndex].id)
        assertNull(updatedState.currentFaceUpCard)
        assertNull(updatedState.auctionState)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_finishRound_wrapsLastPlayerBackToFirstPlayer() {
        val state =
            GameState(
                phase = GamePhase.ROUND_END,
                roundNumber = 3,
                players = listOf(player("player-1"), player("player-2"), player("player-3")),
                currentPlayerIndex = 2,
                deck = AnimalDeck(listOf(AnimalCard(id = "next-card", type = AnimalType.DOG))),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.finishRound()

        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
        assertEquals(4, updatedState.roundNumber)
        assertEquals(0, updatedState.currentPlayerIndex)
        assertEquals("player-1", updatedState.players[updatedState.currentPlayerIndex].id)
    }

    @Test
    fun test_auctionResolutionThenFinishRoundAdvancesToNextPlayableTurn() {
        val state =
            GameState(
                phase = GamePhase.AUCTION,
                roundNumber = 1,
                players = listOf(player("player-1"), player("player-2")),
                currentPlayerIndex = 0,
                deck = AnimalDeck(listOf(AnimalCard(id = "next-card", type = AnimalType.DOG))),
                auctionState = auctionFixture(isClosed = true),
            )
        val session = GameSession.fromState("game-1", state)

        val roundEndState = session.resolveAuction(auctioneerBuysCard = false)
        val nextTurnState = session.finishRound()

        assertEquals(GamePhase.ROUND_END, roundEndState.phase)
        assertEquals(GamePhase.PLAYER_TURN, nextTurnState.phase)
        assertEquals(2, nextTurnState.roundNumber)
        assertEquals(1, nextTurnState.currentPlayerIndex)
        assertEquals("player-2", nextTurnState.players[nextTurnState.currentPlayerIndex].id)
        assertEquals(listOf("auction-card"), nextTurnState.players[0].animals.map { it.id })
        assertNull(nextTurnState.currentFaceUpCard)
        assertNull(nextTurnState.auctionState)
        assertNull(nextTurnState.tradeState)
    }

    @Test
    fun test_tradeResolutionThenFinishRoundAdvancesToNextPlayableTurn() {
        val state =
            tradeState(
                offeredMoneyCardIds = listOf("m-10"),
                offeredMoney = 10,
            ).copy(
                deck = AnimalDeck(listOf(AnimalCard(id = "next-card", type = AnimalType.DOG))),
            )
        val session = GameSession.fromState("game-1", state)

        val roundEndState =
            session.respondToTrade(
                respondingPlayerId = "player-2",
                acceptsOffer = true,
            )
        val nextTurnState = session.finishRound()

        assertEquals(GamePhase.ROUND_END, roundEndState.phase)
        assertEquals(GamePhase.PLAYER_TURN, nextTurnState.phase)
        assertEquals(2, nextTurnState.roundNumber)
        assertEquals(1, nextTurnState.currentPlayerIndex)
        assertEquals("player-2", nextTurnState.players[nextTurnState.currentPlayerIndex].id)
        assertEquals(
            listOf("cow-1", "cow-2", "cow-3"),
            nextTurnState.players[0].animals.map { animal -> animal.id },
        )
        assertNull(nextTurnState.currentFaceUpCard)
        assertNull(nextTurnState.auctionState)
        assertNull(nextTurnState.tradeState)
    }

    @Test
    fun test_finishRound_finishesGameWhenDeckIsEmpty() {
        val state =
            GameState(
                phase = GamePhase.ROUND_END,
                roundNumber = 1,
                players = listOf(player("player-1")),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.finishRound()

        assertEquals(GamePhase.FINISHED, updatedState.phase)
        assertEquals(2, updatedState.roundNumber)
    }

    @Test
    fun test_finishRound_finishesGameWhenPlayersAreMissing() {
        val state =
            GameState(
                phase = GamePhase.ROUND_END,
                roundNumber = 1,
                currentFaceUpCard = AnimalCard(id = "1", type = AnimalType.COW),
                auctionState = auctionFixture(AnimalCard(id = "2", type = AnimalType.DOG)),
            )
        val session = GameSession.fromState("game-1", state)

        val updatedState = session.finishRound()

        assertEquals(GamePhase.FINISHED, updatedState.phase)
        assertNull(updatedState.currentFaceUpCard)
        assertNull(updatedState.auctionState)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_finishRound_rejectsPlayerTurn() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.finishRound()
        }
    }

    @Test
    fun test_finishRound_rejectsActiveAuction() {
        val state = activeAuctionState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.finishRound()
        }
    }

    @Test
    fun test_finishRound_rejectsActiveTrade() {
        val state = tradeState()
        val session = GameSession.fromState("game-1", state)

        assertFailsWith<IllegalStateException> {
            session.finishRound()
        }
    }

    /**
     * Helper that builds a GameState in TRADE phase with two players that share a COW
     * animal type so it can be used as a fixture for OfferTrade and RespondToTrade tests.
     */
    private fun tradeState(
        offeredMoneyCardIds: List<String> = emptyList(),
        offeredMoney: Int = 0,
    ): GameState {
        val initiator =
            PlayerState(
                id = "player-1",
                name = "player-1",
                animals = listOf(AnimalCard(id = "cow-1", type = AnimalType.COW)),
                moneyCards =
                    listOf(
                        MoneyCard(id = "m-10", value = 10),
                        MoneyCard(id = "m-50", value = 50),
                    ),
            )
        val challenged =
            PlayerState(
                id = "player-2",
                name = "player-2",
                animals =
                    listOf(
                        AnimalCard(id = "cow-2", type = AnimalType.COW),
                        AnimalCard(id = "cow-3", type = AnimalType.COW),
                    ),
            )
        return GameState(
            phase = GamePhase.TRADE,
            roundNumber = 1,
            currentPlayerIndex = 0,
            players = listOf(initiator, challenged),
            hostPlayerId = "player-1",
            tradeState =
                TradeState(
                    initiatingPlayerId = "player-1",
                    challengedPlayerId = "player-2",
                    requestedAnimalType = AnimalType.COW,
                    offeredMoney = offeredMoney,
                    offeredMoneyCardIds = offeredMoneyCardIds,
                ),
        )
    }

    private fun player(
        id: String,
        animals: List<AnimalCard> = emptyList(),
        moneyCards: List<MoneyCard> = emptyList(),
    ): PlayerState =
        PlayerState(
            id = id,
            name = id,
            animals = animals,
            moneyCards = moneyCards,
        )

    private fun tradeReadyState(
        initiatingMoneyCards: List<MoneyCard> = emptyList(),
        challengedMoneyCards: List<MoneyCard> = emptyList(),
    ): GameState =
        GameState(
            phase = GamePhase.PLAYER_TURN,
            roundNumber = 1,
            players =
                listOf(
                    player(
                        id = "player-1",
                        animals = listOf(AnimalCard(id = "cow-1", type = AnimalType.COW)),
                        moneyCards = initiatingMoneyCards,
                    ),
                    player(
                        id = "player-2",
                        animals = listOf(AnimalCard(id = "cow-2", type = AnimalType.COW)),
                        moneyCards = challengedMoneyCards,
                    ),
                ),
            hostPlayerId = "player-1",
        )

    private fun activeTradeState(
        initiatingMoneyCards: List<MoneyCard> = emptyList(),
        challengedMoneyCards: List<MoneyCard> = emptyList(),
        offeredMoneyCardIds: List<String> = emptyList(),
    ): GameState {
        val baseState =
            tradeReadyState(
                initiatingMoneyCards = initiatingMoneyCards,
                challengedMoneyCards = challengedMoneyCards,
            )
        val offeredMoney =
            initiatingMoneyCards
                .filter { moneyCard ->
                    moneyCard.id in offeredMoneyCardIds
                }.sumOf { moneyCard ->
                    moneyCard.value
                }

        return baseState.copy(
            phase = GamePhase.TRADE,
            tradeState =
                TradeState(
                    initiatingPlayerId = "player-1",
                    challengedPlayerId = "player-2",
                    requestedAnimalType = AnimalType.COW,
                    step = TradeStep.WAITING_FOR_RESPONSE,
                    offeredMoney = offeredMoney,
                    offeredMoneyCardIds = offeredMoneyCardIds,
                    offeredMoneyCardCount = offeredMoneyCardIds.size,
                ),
        )
    }

    private fun activeAuctionState(): GameState =
        activeAuctionState(
            auctionState = auctionFixture(),
        )

    private fun activeAuctionState(
        auctionState: AuctionState = auctionFixture(),
        auctioneerMoneyCards: List<MoneyCard> =
            listOf(
                MoneyCard(id = "p1-money-10", value = 10),
                MoneyCard(id = "p1-money-50", value = 50),
            ),
        bidderMoneyCards: List<MoneyCard> =
            listOf(
                MoneyCard(id = "p2-money-10", value = 10),
                MoneyCard(id = "p2-money-50", value = 50),
            ),
    ): GameState =
        GameState(
            phase = GamePhase.AUCTION,
            players =
                listOf(
                    player("player-1", moneyCards = auctioneerMoneyCards),
                    player("player-2", moneyCards = bidderMoneyCards),
                    player("player-3"),
                ),
            hostPlayerId = "player-1",
            auctionState = auctionState,
        )

    private fun auctionFixture(
        auctionCard: AnimalCard = AnimalCard(id = "auction-card", type = AnimalType.COW),
        highestBid: Int = 0,
        highestBidderId: String? = null,
        isClosed: Boolean = false,
    ): AuctionState =
        AuctionState(
            auctionCard = auctionCard,
            auctioneerId = "player-1",
            highestBid = highestBid,
            highestBidderId = highestBidderId,
            isClosed = isClosed,
        )
}
