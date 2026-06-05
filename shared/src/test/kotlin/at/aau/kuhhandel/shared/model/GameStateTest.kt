package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateTest {
    private val baseState =
        GameState(
            phase = GamePhase.TRADE_RESPONSE,
            roundNumber = 1,
            deck = AnimalDeck(cards = listOf(AnimalCard("donkey-1", AnimalType.DONKEY))),
            currentPlayerIndex = 0,
            players =
                listOf(
                    Player(
                        id = "player-1",
                        name = "Player 1",
                        moneyCards = listOf(MoneyCard("money-10-1", 10)),
                    ),
                    Player(
                        id = "player-2",
                        name = "Player 3",
                        animals = listOf(AnimalCard("cow-1", AnimalType.COW)),
                        moneyCards = listOf(MoneyCard("money-0-1", 0), MoneyCard("money-20-1", 20)),
                    ),
                    Player(
                        id = "player-3",
                        name = "Player 3",
                        moneyCards = listOf(MoneyCard("money-100-1", 100)),
                    ),
                ),
            hostPlayerId = "player-1",
            auctionState = null,
            tradeState =
                TradeState(
                    initiatorId = "player-1",
                    targetId = "player-2",
                    requestedAnimalType = AnimalType.COW,
                    offeredMoneyCards = setOf(MoneyCard("money-50-1", 50)),
                    counterOfferedMoneyCards = null,
                ),
            lastEvent = null,
        )

    @Test
    fun test_defaultGameState() {
        val state = GameState()

        assertEquals(GamePhase.NOT_STARTED, state.phase)
        assertEquals(0, state.roundNumber)
        assertEquals(-1, state.currentPlayerIndex)
        assertNull(state.currentFaceUpCard)
        assertTrue(state.players.isEmpty())
        assertTrue(state.deck.isEmpty())
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_gameState_withCustomValues() {
        val card = AnimalCard(id = "1", type = AnimalType.COW)
        val auctionState = AuctionState(auctionCard = card, auctioneerId = "p1")
        val tradeState =
            TradeState(
                initiatorId = "p1",
                targetId = "p2",
                requestedAnimalType = AnimalType.DOG,
            )

        val state =
            GameState(
                phase = GamePhase.AUCTION_BIDDING,
                roundNumber = 2,
                deck = AnimalDeck(listOf(card)),
                currentFaceUpCard = card,
                currentPlayerIndex = 1,
                players = emptyList(),
                auctionState = auctionState,
                tradeState = tradeState,
            )

        assertEquals(GamePhase.AUCTION_BIDDING, state.phase)
        assertEquals(2, state.roundNumber)
        assertEquals(card, state.currentFaceUpCard)
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(auctionState, state.auctionState)
        assertEquals(tradeState, state.tradeState)
        assertEquals(1, state.deck.size())
    }

    @Test
    fun test_createViewForPlayer_showsTradeData_forTradeInitiator() {
        val view = baseState.createViewForPlayer("player-1")

        assertEquals(baseState.phase, view.phase)
        assertEquals("player-1", view.localPlayer.id)
        assertTrue(view.opponents.any { it.id == "player-2" })
        assertTrue(view.opponents.any { it.id == "player-3" })
        assertEquals(baseState.hostPlayerId, view.hostPlayerId)
        assertEquals(baseState.roundNumber, view.roundNumber)
        assertEquals(baseState.currentPlayerIndex, view.currentPlayerIndex)
        assertEquals(baseState.deck.size(), view.deckSize)
        assertEquals(baseState.auctionState, view.auctionState)
        assertEquals(baseState.lastEvent, view.lastEvent)

        val tradeState = baseState.tradeState
        val tradeView = view.tradeState
        assertNotNull(tradeView)
        assertEquals(tradeState?.initiatorId, tradeView.initiatorId)
        assertEquals(tradeState?.targetId, tradeView.targetId)
        assertEquals(tradeState?.requestedAnimalType, tradeView.requestedAnimalType)
        assertEquals(tradeState?.offeredMoneyCards?.size, tradeView.initiatorCardCount)
        assertEquals(tradeState?.counterOfferedMoneyCards?.size, tradeView.targetCardCount)
        assertEquals(tradeState?.offeredMoneyCards, tradeView.visibleInitiatorCards?.toSet())
        assertEquals(tradeState?.counterOfferedMoneyCards, tradeView.visibleTargetCards?.toSet())
    }

    @Test
    fun test_createViewForPlayer_doesNotShowTradeData_forTradeObserverBeforeReveal() {
        val view = baseState.createViewForPlayer("player-3")

        assertEquals(baseState.phase, view.phase)
        assertEquals("player-3", view.localPlayer.id)
        assertTrue(view.opponents.any { it.id == "player-1" })
        assertTrue(view.opponents.any { it.id == "player-2" })
        assertEquals(baseState.hostPlayerId, view.hostPlayerId)
        assertEquals(baseState.roundNumber, view.roundNumber)
        assertEquals(baseState.currentPlayerIndex, view.currentPlayerIndex)
        assertEquals(baseState.deck.size(), view.deckSize)
        assertEquals(baseState.auctionState, view.auctionState)
        assertEquals(baseState.lastEvent, view.lastEvent)

        val tradeState = baseState.tradeState
        val tradeView = view.tradeState
        assertNotNull(tradeView)
        assertEquals(tradeState?.initiatorId, tradeView.initiatorId)
        assertEquals(tradeState?.targetId, tradeView.targetId)
        assertEquals(tradeState?.requestedAnimalType, tradeView.requestedAnimalType)
        assertEquals(tradeState?.offeredMoneyCards?.size, tradeView.initiatorCardCount)
        assertEquals(tradeState?.counterOfferedMoneyCards?.size, tradeView.targetCardCount)
        assertNull(tradeView.visibleInitiatorCards)
        assertNull(tradeView.visibleTargetCards)
    }

    @Test
    fun test_createViewForPlayer_showsTradeData_forTradeObserverDuringReveal() {
        val revealState =
            baseState.copy(
                phase = GamePhase.TRADE_RESULT,
                tradeState =
                    baseState.tradeState?.copy(
                        counterOfferedMoneyCards = setOf(MoneyCard("money-0-2", 0)),
                    ),
            )

        val view = revealState.createViewForPlayer("player-3")
        val tradeState = revealState.tradeState
        val tradeView = view.tradeState

        assertNotNull(tradeView)
        assertEquals(tradeState?.initiatorId, tradeView.initiatorId)
        assertEquals(tradeState?.targetId, tradeView.targetId)
        assertEquals(tradeState?.requestedAnimalType, tradeView.requestedAnimalType)
        assertEquals(tradeState?.offeredMoneyCards?.size, tradeView.initiatorCardCount)
        assertEquals(tradeState?.counterOfferedMoneyCards?.size, tradeView.targetCardCount)
        assertEquals(tradeState?.offeredMoneyCards, tradeView.visibleInitiatorCards?.toSet())
        assertEquals(tradeState?.counterOfferedMoneyCards, tradeView.visibleTargetCards?.toSet())
    }

    @Test
    fun test_updatePlayer_updatesPlayer_ifPlayerExists() {
        val updatedState =
            baseState.updatePlayer("player-2") { player ->
                player.copy(moneyCards = player.moneyCards - MoneyCard("money-20-1", 20))
            }

        val updatedPlayer = updatedState.players.first { it.id == "player-2" }
        assertEquals(1, updatedPlayer.moneyCards.size)
        assertTrue(updatedPlayer.moneyCards.none { it.id == "money-20-1" })

        assertEquals(
            baseState.players.filter { it.id != "player-2" },
            updatedState.players.filter { it.id != "player-2" },
        )
    }

    @Test
    fun test_updatePlayer_throws_ifPlayerDoesNotExist() {
        assertThrows<IllegalStateException> { baseState.updatePlayer("player-4") { it } }
    }
}
