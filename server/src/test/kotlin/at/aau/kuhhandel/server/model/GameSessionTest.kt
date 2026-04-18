package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionTest {
    @Test
    fun test_newSession_isNotStarted() {
        val session = GameSession("12345")

        assertEquals("12345", session.gameId)
        assertEquals(GamePhase.NOT_STARTED, session.gameState.phase)
        assertTrue(session.gameState.deck.isEmpty())
        assertNull(session.gameState.currentFaceUpCard)
        assertEquals(0, session.gameState.currentPlayerIndex)
        assertTrue(session.gameState.players.isEmpty())
        assertNull(session.gameState.auctionState)
        assertNull(session.gameState.tradeState)
    }

    @Test
    fun test_startGame_initializesGame() {
        val session = GameSession("12345")

        val state = session.startGame()

        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(3, state.deck.size())
        assertNull(state.currentFaceUpCard)
        assertEquals(0, state.currentPlayerIndex)
        assertTrue(state.players.isEmpty())
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_startGame_updatesStoredState() {
        val session = GameSession("12345")

        session.startGame()

        assertEquals(GamePhase.PLAYER_TURN, session.gameState.phase)
        assertEquals(3, session.gameState.deck.size())
        assertNull(session.gameState.currentFaceUpCard)
    }

    @Test
    fun test_revealNextCard_revealsCard() {
        val session = GameSession("12345")
        session.startGame()

        val state = session.revealNextCard()

        assertNotNull(state.currentFaceUpCard)
        assertEquals(2, state.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_revealNextCard_updatesStoredState() {
        val session = GameSession("12345")
        session.startGame()

        session.revealNextCard()

        assertNotNull(session.gameState.currentFaceUpCard)
        assertEquals(2, session.gameState.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, session.gameState.phase)
    }

    @Test
    fun test_revealNextCard_lastCardDoesNotImmediatelyFinishGame() {
        val session = GameSession("12345")
        session.startGame()

        session.revealNextCard()
        session.revealNextCard()
        val stateAfterLastCard = session.revealNextCard()

        assertNotNull(stateAfterLastCard.currentFaceUpCard)
        assertEquals(0, stateAfterLastCard.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, stateAfterLastCard.phase)
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckAlreadyEmpty() {
        val session = GameSession("12345")
        session.startGame()

        session.revealNextCard()
        session.revealNextCard()
        session.revealNextCard()
        val finalState = session.revealNextCard()

        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
        assertNull(finalState.auctionState)
        assertNull(finalState.tradeState)
    }
}
