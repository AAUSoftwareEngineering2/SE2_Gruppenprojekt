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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateMachineTest {
    private companion object {
        const val CARDS_PER_ANIMAL_TYPE = 4
        val FULL_DECK_SIZE = AnimalType.entries.size * CARDS_PER_ANIMAL_TYPE
        val STARTING_MONEY_VALUES = listOf(0, 0, 10, 10, 10, 10, 50)
    }

    private val stateMachine = GameStateMachine()

    @Test
    fun test_addPlayer_addsFirstPlayerAndSetsHost() {
        val state = GameState()

        val updatedState = stateMachine.apply(state, GameCommand.AddPlayer("player-1", "Player 1"))

        assertEquals(1, updatedState.players.size)
        assertEquals("player-1", updatedState.players[0].id)
        assertEquals("Player 1", updatedState.players[0].name)
        assertEquals("player-1", updatedState.hostPlayerId)
    }

    @Test
    fun test_addPlayer_addsSubsequentPlayerAndDoesNotChangeHost() {
        var state = GameState()
        state = stateMachine.apply(state, GameCommand.AddPlayer("player-1", "Player 1"))

        val updatedState = stateMachine.apply(state, GameCommand.AddPlayer("player-2", "Player 2"))

        assertEquals(2, updatedState.players.size)
        assertEquals("player-1", updatedState.players[0].id)
        assertEquals("Player 1", updatedState.players[0].name)
        assertEquals("player-2", updatedState.players[1].id)
        assertEquals("Player 2", updatedState.players[1].name)
        assertEquals("player-1", updatedState.hostPlayerId)
    }

    @Test
    fun test_addPlayer_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.AddPlayer("player-1", "Player 1"),
            )
        }
    }

    @Test
    fun test_addPlayer_rejectsExistingId() {
        val state = GameState(players = listOf(PlayerState("player-1", "Player 1")))

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.AddPlayer("player-1", "Player with same ID"),
            )
        }
    }

    @Test
    fun test_removePlayer_removesNonHostAndDoesNotChangeHost() {
        val state =
            GameState(
                players =
                    listOf(
                        PlayerState("player-1", "Player 1"),
                        PlayerState("player-2", "Player 2"),
                    ),
                hostPlayerId = "player-2",
            )

        val updatedState = stateMachine.apply(state, GameCommand.RemovePlayer("player-1"))

        assertEquals(listOf(PlayerState("player-2", "Player 2")), updatedState.players)
        assertEquals("player-2", updatedState.hostPlayerId)
    }

    @Test
    fun test_removePlayer_removesHostAndUpdatesHost() {
        val state =
            GameState(
                players =
                    listOf(
                        PlayerState("player-1", "Player 1"),
                        PlayerState("player-2", "Player 2"),
                    ),
                hostPlayerId = "player-1",
            )

        val updatedState = stateMachine.apply(state, GameCommand.RemovePlayer("player-1"))

        assertEquals(listOf(PlayerState("player-2", "Player 2")), updatedState.players)
        assertEquals("player-2", updatedState.hostPlayerId)
    }

    @Test
    fun test_removePlayer_removesLastPlayer() {
        val state =
            GameState(
                players = listOf(PlayerState("player-1", "Player 1")),
                hostPlayerId = "player-1",
            )

        val updatedState = stateMachine.apply(state, GameCommand.RemovePlayer("player-1"))

        assertTrue(updatedState.players.isEmpty())
        assertNull(updatedState.hostPlayerId)
    }

    @Test
    fun test_removePlayer_rejectsWrongPhase() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players =
                    listOf(
                        PlayerState("player-1", "Player 1"),
                    ),
            )

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.RemovePlayer("player-1"),
            )
        }
    }

    @Test
    fun test_removePlayer_rejectsNonexistentId() {
        val state = GameState(players = listOf(PlayerState("player-1", "Player 1")))

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.RemovePlayer("player-2"),
            )
        }
    }

    @Test
    fun test_startGame_initializesPlayerTurnAndRoundOne() {
        val state =
            GameState(players = listOf(player("player-1"), player("player-2"), player("player-3")))

        val updatedState = stateMachine.apply(state, GameCommand.StartGame)

        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
        assertEquals(1, updatedState.roundNumber)
        assertEquals(FULL_DECK_SIZE, updatedState.deck.size())
        assertEquals(0, updatedState.currentPlayerIndex)
        assertEquals("player-1", updatedState.activePlayerId)
        assertNull(updatedState.currentFaceUpCard)
        assertNull(updatedState.auctionState)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_startGame_createsFullAnimalDeck() {
        val state =
            GameState(
                players =
                    listOf(
                        player("player-1"),
                        player("player-2"),
                        player("player-3"),
                    ),
            )

        val updatedState = stateMachine.apply(state, GameCommand.StartGame)

        AnimalType.entries.forEach { type ->
            assertEquals(
                CARDS_PER_ANIMAL_TYPE,
                updatedState.deck.cards.count { card -> card.type == type },
            )
        }
        assertEquals(
            FULL_DECK_SIZE,
            updatedState.deck.cards
                .map { card -> card.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun test_startGame_dealsStartingMoneyToEveryPlayer() {
        val state =
            GameState(
                players =
                    listOf(
                        player("player-1"),
                        player("player-2"),
                        player("player-3"),
                    ),
            )

        val updatedState = stateMachine.apply(state, GameCommand.StartGame)

        updatedState.players.forEach { player ->
            assertEquals(STARTING_MONEY_VALUES, player.moneyCards.map { card -> card.value })
            assertEquals(90, player.totalMoney())
            assertEquals(emptyList(), player.animals)
        }
        assertEquals(
            updatedState.players.sumOf { player -> player.moneyCards.size },
            updatedState.players
                .flatMap { player -> player.moneyCards }
                .map { card -> card.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun test_startGame_rejectsWithOnlyTwoPlayers() {
        val state = GameState(players = listOf(player("player-1"), player("player-2")))

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.StartGame)
        }
    }

    @Test
    fun test_addPlayer_rejectsWhenFull() {
        var state = GameState()
        for (i in 1..5) {
            state = stateMachine.apply(state, GameCommand.AddPlayer("p$i", "Player $i"))
        }

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.AddPlayer("p6", "Player 6"))
        }
    }

    @Test
    fun test_startGame_rejectsAlreadyStartedGame() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.StartGame)
        }
    }

    @Test
    fun test_revealCard_drawsTopCardDuringPlayerTurn() {
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

        val updatedState = stateMachine.apply(state, GameCommand.RevealCard)

        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
        assertEquals(1, updatedState.deck.size())
        assertEquals(AnimalType.DOG, updatedState.currentFaceUpCard?.type)
        assertNull(updatedState.auctionState)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_revealCard_finishesGameWhenDeckIsAlreadyEmpty() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                roundNumber = 1,
                players = listOf(player("player-1")),
            )

        val updatedState = stateMachine.apply(state, GameCommand.RevealCard)

        assertEquals(GamePhase.FINISHED, updatedState.phase)
        assertNull(updatedState.currentFaceUpCard)
    }

    @Test
    fun test_revealCard_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.AUCTION)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.RevealCard)
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

        val updatedState = stateMachine.apply(state, GameCommand.ChooseAuction)

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
    fun test_chooseAuction_rejectsEmptyDeck() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players = listOf(player("player-1")),
            )

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.ChooseAuction)
        }
    }

    @Test
    fun test_chooseAuction_rejectsWrongPhase() {
        val state =
            GameState(
                phase = GamePhase.TRADE,
                deck = AnimalDeck(listOf(AnimalCard(id = "1", type = AnimalType.COW))),
            )

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.ChooseAuction)
        }
    }

    @Test
    fun test_placeBid_updatesHighestBidAndBidder() {
        val state = activeAuctionState()

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-2", amount = 10),
            )

        assertEquals(GamePhase.AUCTION, updatedState.phase)
        assertEquals(10, updatedState.auctionState?.highestBid)
        assertEquals("player-2", updatedState.auctionState?.highestBidderId)
    }

    @Test
    fun test_placeBid_rejectsOutsideAuction() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-2", amount = 10),
            )
        }
    }

    @Test
    fun test_placeBid_rejectsMissingAuctionState() {
        val state =
            GameState(
                phase = GamePhase.AUCTION,
                players = listOf(player("player-1"), player("player-2")),
            )

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-2", amount = 10),
            )
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

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-3", amount = 20),
            )
        }
    }

    @Test
    fun test_placeBid_rejectsAuctioneer() {
        val state = activeAuctionState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-1", amount = 10),
            )
        }
    }

    @Test
    fun test_placeBid_rejectsUnknownBidder() {
        val state = activeAuctionState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-unknown", amount = 10),
            )
        }
    }

    @Test
    fun test_placeBid_rejectsLowerOrEqualBid() {
        val state =
            activeAuctionState(
                auctionState =
                    auctionFixture(
                        highestBid = 10,
                        highestBidderId = "player-2",
                    ),
            )

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-3", amount = 10),
            )
        }
    }

    @Test
    fun test_placeBid_rejectsBidderWithoutEnoughMoney() {
        val state =
            activeAuctionState(
                bidderMoneyCards = listOf(MoneyCard(id = "p2-money-10", value = 10)),
            )

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.PlaceBid(bidderId = "player-2", amount = 20),
            )
        }
    }

    @Test
    fun test_closeAuction_marksAuctionClosed() {
        val state = activeAuctionState()

        val updatedState = stateMachine.apply(state, GameCommand.CloseAuction)

        assertEquals(true, updatedState.auctionState?.isClosed)
    }

    @Test
    fun test_closeAuction_rejectsOutsideAuction() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.CloseAuction)
        }
    }

    @Test
    fun test_closeAuction_rejectsMissingAuctionState() {
        val state =
            GameState(
                phase = GamePhase.AUCTION,
                players = listOf(player("player-1"), player("player-2")),
            )

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(state, GameCommand.CloseAuction)
        }
    }

    @Test
    fun test_closeAuction_rejectsAlreadyClosedAuction() {
        val state =
            activeAuctionState(
                auctionFixture(isClosed = true),
            )

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.CloseAuction)
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.ResolveAuction(auctioneerBuysCard = false),
            )

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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.ResolveAuction(auctioneerBuysCard = true),
            )

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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.ResolveAuction(auctioneerBuysCard = false),
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertEquals(
            listOf(AnimalCard(id = "auction-card", type = AnimalType.COW)),
            updatedState.players[0].animals,
        )
        assertNull(updatedState.auctionState)
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

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.ResolveAuction(auctioneerBuysCard = true),
            )
        }
    }

    @Test
    fun test_resolveAuction_rejectsOutsideAuction() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.ResolveAuction(auctioneerBuysCard = false),
            )
        }
    }

    @Test
    fun test_resolveAuction_rejectsOpenAuction() {
        val state = activeAuctionState()

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.ResolveAuction(auctioneerBuysCard = false),
            )
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-2",
                    animalType = AnimalType.COW,
                ),
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-2",
                    animalType = AnimalType.COW,
                    offeredMoneyCardIds = listOf("money-10", "money-50"),
                ),
            )

        assertEquals(60, updatedState.tradeState?.offeredMoney)
        assertEquals(listOf("money-10", "money-50"), updatedState.tradeState?.offeredMoneyCardIds)
        assertEquals(2, updatedState.tradeState?.offeredMoneyCardCount)
        assertEquals(TradeStep.WAITING_FOR_RESPONSE, updatedState.tradeState?.step)
    }

    @Test
    fun test_chooseTrade_rejectsUnknownOfferedMoneyCard() {
        val state = tradeReadyState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-2",
                    animalType = AnimalType.COW,
                    offeredMoneyCardIds = listOf("missing-card"),
                ),
            )
        }
    }

    @Test
    fun test_respondToTrade_acceptOfferMovesAnimalToInitiatingPlayerAndMoneyToChallengedPlayer() {
        val state =
            activeTradeState(
                initiatingMoneyCards = listOf(MoneyCard(id = "money-10", value = 10)),
                offeredMoneyCardIds = listOf("money-10"),
            )

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = true,
                ),
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = true,
                    counterOfferedMoneyCardIds = listOf("money-50"),
                ),
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = false,
                    counterOfferedMoneyCardIds = listOf("money-50"),
                ),
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = false,
                    counterOfferedMoneyCardIds = listOf("money-b"),
                ),
            )

        assertEquals(listOf("cow-1", "cow-2"), updatedState.players[0].animals.map { it.id })
        assertEquals(emptyList(), updatedState.players[1].animals)
        assertEquals(listOf("money-b"), updatedState.players[0].moneyCards.map { it.id })
        assertEquals(listOf("money-a"), updatedState.players[1].moneyCards.map { it.id })
    }

    @Test
    fun test_respondToTrade_rejectsWrongPhaseWithoutOffer() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = true,
                ),
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsWrongResponder() {
        val state = activeTradeState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-1",
                    acceptsOffer = true,
                ),
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsUnknownCounterOfferCard() {
        val state = activeTradeState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = false,
                    counterOfferedMoneyCardIds = listOf("missing-card"),
                ),
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

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = true,
                ),
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

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = true,
                ),
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsSelfChallenge() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players = listOf(player("player-1")),
            )

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-1",
                    animalType = AnimalType.COW,
                ),
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.AUCTION)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-2",
                    animalType = AnimalType.COW,
                ),
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

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-2",
                    animalType = AnimalType.COW,
                ),
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

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-2",
                    animalType = AnimalType.COW,
                ),
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

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(
                    challengedPlayerId = "player-2",
                    animalType = AnimalType.COW,
                ),
            )
        }
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

        val updatedState = stateMachine.apply(state, GameCommand.FinishRound)

        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
        assertEquals(2, updatedState.roundNumber)
        assertEquals(1, updatedState.currentPlayerIndex)
        assertEquals("player-2", updatedState.activePlayerId)
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

        val updatedState = stateMachine.apply(state, GameCommand.FinishRound)

        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
        assertEquals(4, updatedState.roundNumber)
        assertEquals(0, updatedState.currentPlayerIndex)
        assertEquals("player-1", updatedState.activePlayerId)
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

        val roundEndState =
            stateMachine.apply(
                state,
                GameCommand.ResolveAuction(auctioneerBuysCard = false),
            )
        val nextTurnState = stateMachine.apply(roundEndState, GameCommand.FinishRound)

        assertEquals(GamePhase.ROUND_END, roundEndState.phase)
        assertEquals(GamePhase.PLAYER_TURN, nextTurnState.phase)
        assertEquals(2, nextTurnState.roundNumber)
        assertEquals(1, nextTurnState.currentPlayerIndex)
        assertEquals("player-2", nextTurnState.activePlayerId)
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

        val roundEndState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = true,
                ),
            )
        val nextTurnState = stateMachine.apply(roundEndState, GameCommand.FinishRound)

        assertEquals(GamePhase.ROUND_END, roundEndState.phase)
        assertEquals(GamePhase.PLAYER_TURN, nextTurnState.phase)
        assertEquals(2, nextTurnState.roundNumber)
        assertEquals(1, nextTurnState.currentPlayerIndex)
        assertEquals("player-2", nextTurnState.activePlayerId)
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

        val updatedState = stateMachine.apply(state, GameCommand.FinishRound)

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

        val updatedState = stateMachine.apply(state, GameCommand.FinishRound)

        assertEquals(GamePhase.FINISHED, updatedState.phase)
        assertNull(updatedState.currentFaceUpCard)
        assertNull(updatedState.auctionState)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_finishRound_rejectsPlayerTurn() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.FinishRound)
        }
    }

    @Test
    fun test_finishRound_rejectsActiveAuction() {
        val state = activeAuctionState()

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.FinishRound)
        }
    }

    @Test
    fun test_finishRound_rejectsActiveTrade() {
        val state = tradeState()

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.FinishRound)
        }
    }

    @Test
    fun test_offerTrade_setsOfferedMoneyAndCardsOnTradeState() {
        val state = tradeState()

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.OfferTrade(offeredMoneyCardIds = listOf("m-10", "m-50")),
            )

        assertEquals(GamePhase.TRADE, updatedState.phase)
        assertEquals(60, updatedState.tradeState?.offeredMoney)
        assertEquals(listOf("m-10", "m-50"), updatedState.tradeState?.offeredMoneyCardIds)
    }

    @Test
    fun test_offerTrade_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.PLAYER_TURN)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(state, GameCommand.OfferTrade(offeredMoneyCardIds = listOf("m-10")))
        }
    }

    @Test
    fun test_offerTrade_rejectsEmptyOffer() {
        val state = tradeState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(state, GameCommand.OfferTrade(offeredMoneyCardIds = emptyList()))
        }
    }

    @Test
    fun test_offerTrade_rejectsUnknownMoneyCardId() {
        val state = tradeState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.OfferTrade(offeredMoneyCardIds = listOf("does-not-exist")),
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = true,
                ),
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

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = false,
                ),
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

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", acceptsOffer = true),
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsNonChallengedResponder() {
        val state =
            tradeState(
                offeredMoneyCardIds = listOf("m-10"),
                offeredMoney = 10,
            )

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(respondingPlayerId = "player-1", acceptsOffer = true),
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsAcceptanceWithoutPriorOffer() {
        val state = tradeState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", acceptsOffer = true),
            )
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

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(state, GameCommand.OfferTrade(offeredMoneyCardIds = listOf("m-1")))
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

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", acceptsOffer = false),
            )
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

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", acceptsOffer = true),
            )
        }
    }

    @Test
    fun test_respondToTrade_noAcceptanceNoCounterOfferMovesToRoundEnd() {
        val state =
            activeTradeState(
                initiatingMoneyCards = listOf(MoneyCard(id = "m-10", value = 10)),
                offeredMoneyCardIds = listOf("m-10"),
            )

        val updatedState =
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(
                    respondingPlayerId = "player-2",
                    acceptsOffer = false,
                    counterOfferedMoneyCardIds = emptyList(),
                ),
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertNull(updatedState.tradeState)
    }

    @Test
    fun test_transferMoneyCards_returnsPlayersIfIdsEmpty() {
        // This is internal, but we can hit it via RespondToTrade with empty lists
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
