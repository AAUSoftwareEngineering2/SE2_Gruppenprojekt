package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateTest {
    @Test
    fun test_defaultGameState() {
        val state = GameState()

        assertEquals(GamePhase.NOT_STARTED, state.phase)
        assertEquals(0, state.currentPlayerIndex)
        assertNull(state.currentFaceUpCard)
        assertTrue(state.players.isEmpty())
        assertTrue(state.deck.isEmpty())
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }
}
