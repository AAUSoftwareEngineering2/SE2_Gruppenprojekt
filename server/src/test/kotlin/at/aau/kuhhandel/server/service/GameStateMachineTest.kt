package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
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
    private val stateMachine = GameStateMachine()

    @Test
    fun test_startGame_initializesPlayerTurnAndRoundOne() {
        val state = GameState(players = listOf(player("player-1")))

        val updatedState = stateMachine.apply(state, GameCommand.StartGame)

        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
        assertEquals(1, updatedState.roundNumber)
        assertEquals(3, updatedState.deck.size())
        assertEquals(0, updatedState.currentPlayerIndex)
        assertNull(updatedState.currentFaceUpCard)
        assertNull(updatedState.auctionState)
        assertNull(updatedState.tradeState)
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
        assertEquals(
            AuctionState(
                auctionCard = AnimalCard(id = "2", type = AnimalType.DOG),
                auctioneerId = "player-1",
            ),
            updatedState.auctionState,
        )
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
                GameCommand.ChooseTrade(challengedPlayerId = "player-2"),
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
    fun test_chooseTrade_rejectsSelfChallenge() {
        val state =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                players = listOf(player("player-1")),
            )

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(challengedPlayerId = "player-1"),
            )
        }
    }

    @Test
    fun test_chooseTrade_rejectsWrongPhase() {
        val state = GameState(phase = GamePhase.AUCTION)

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.ChooseTrade(challengedPlayerId = "player-2"),
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
                GameCommand.ChooseTrade(challengedPlayerId = "player-2"),
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
                GameCommand.ChooseTrade(challengedPlayerId = "player-2"),
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
                GameCommand.ChooseTrade(challengedPlayerId = "player-2"),
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
        assertNull(updatedState.currentFaceUpCard)
        assertNull(updatedState.auctionState)
        assertNull(updatedState.tradeState)
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
                    accepted = true,
                ),
            )

        assertEquals(GamePhase.ROUND_END, updatedState.phase)
        assertNull(updatedState.tradeState)

        val initiator = updatedState.players.first { it.id == "player-1" }
        val challenged = updatedState.players.first { it.id == "player-2" }

        // Initiator receives one COW from challenged player
        assertEquals(2, initiator.animals.count { it.type == AnimalType.COW })
        assertEquals(1, challenged.animals.count { it.type == AnimalType.COW })

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
                    accepted = false,
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
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", accepted = true),
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
                GameCommand.RespondToTrade(respondingPlayerId = "player-1", accepted = true),
            )
        }
    }

    @Test
    fun test_respondToTrade_rejectsAcceptanceWithoutPriorOffer() {
        val state = tradeState()

        assertFailsWith<IllegalArgumentException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", accepted = true),
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
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", accepted = false),
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

        assertFailsWith<IllegalStateException> {
            stateMachine.apply(
                state,
                GameCommand.RespondToTrade(respondingPlayerId = "player-2", accepted = true),
            )
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
    ): PlayerState =
        PlayerState(
            id = id,
            name = id,
            animals = animals,
        )

    private fun activeAuctionState(): GameState = activeAuctionState(auctionFixture())

    private fun activeAuctionState(auctionState: AuctionState): GameState =
        GameState(
            phase = GamePhase.AUCTION,
            players =
                listOf(
                    player("player-1"),
                    player("player-2"),
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
