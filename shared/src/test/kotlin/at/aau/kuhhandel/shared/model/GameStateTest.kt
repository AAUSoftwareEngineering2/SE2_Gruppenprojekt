package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateTest {
    @Test
    fun test_defaultGameState() {
        val state = GameState()

        assertEquals(GamePhase.NOT_STARTED, state.phase)
        assertEquals(0, state.roundNumber)
        assertEquals(0, state.currentPlayerIndex)
        assertNull(state.activePlayerId)
        assertNull(state.currentFaceUpCard)
        assertTrue(state.players.isEmpty())
        assertTrue(state.deck.isEmpty())
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_gameState_withCustomValues() {
        val card = AnimalCard(id = "1", type = at.aau.kuhhandel.shared.enums.AnimalType.COW)
        val auctionState = AuctionState(auctionCard = card, auctioneerId = "p1")
        val tradeState =
            TradeState(
                initiatingPlayerId = "p1",
                challengedPlayerId = "p2",
                requestedAnimalType = at.aau.kuhhandel.shared.enums.AnimalType.DOG,
            )

        val state =
            GameState(
                phase = GamePhase.AUCTION,
                roundNumber = 2,
                deck = AnimalDeck(listOf(card)),
                currentFaceUpCard = card,
                currentPlayerIndex = 1,
                activePlayerId = "p2",
                players = emptyList(),
                auctionState = auctionState,
                tradeState = tradeState,
            )

        assertEquals(GamePhase.AUCTION, state.phase)
        assertEquals(2, state.roundNumber)
        assertEquals(card, state.currentFaceUpCard)
        assertEquals(1, state.currentPlayerIndex)
        assertEquals("p2", state.activePlayerId)
        assertEquals(auctionState, state.auctionState)
        assertEquals(tradeState, state.tradeState)
        assertEquals(1, state.deck.size())
    }
}
