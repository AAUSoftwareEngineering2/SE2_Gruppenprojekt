package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameManagerTest {
    @Test
    fun test_createTestGame_initialStateIsCorrect() {
        val manager = GameManager()

        val session = manager.createTestGame()

        // Game should start running with no revealed card
        assertEquals(GamePhase.RUNNING, session.gameState.phase)
        assertNull(session.gameState.currentFaceUpCard)

        // Test deck has 3 cards
        assertEquals(3, session.gameState.deck.size())
    }

    @Test
    fun test_getSession_returnsCorrectSession() {
        val manager = GameManager()
        val session = manager.createTestGame()

        val loadedSession = manager.getSession(session.sessionId)

        // Retrieved session should match the original
        assertNotNull(loadedSession)
        assertEquals(session.sessionId, loadedSession.sessionId)
    }

    @Test
    fun test_revealNextCard_revealsCard() {
        val manager = GameManager()
        val session = manager.createTestGame()

        val updatedState = manager.revealNextCard(session.sessionId)

        // A card should now be visible
        assertNotNull(updatedState)
        assertNotNull(updatedState.currentFaceUpCard)
        assertEquals(GamePhase.RUNNING, updatedState.phase)
    }

    @Test
    fun test_revealNextCard_reducesDeckSize() {
        val manager = GameManager()
        val session = manager.createTestGame()

        val beforeSize = session.gameState.deck.size()
        val updatedState = manager.revealNextCard(session.sessionId)
        val afterSize = updatedState?.deck?.size()

        // Deck size should decrease by 1
        assertEquals(beforeSize - 1, afterSize)
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckEmpty() {
        val manager = GameManager()
        val session = manager.createTestGame()

        // Reveal all cards
        manager.revealNextCard(session.sessionId)
        manager.revealNextCard(session.sessionId)
        val finalState = manager.revealNextCard(session.sessionId)

        assertNotNull(finalState)
        assertEquals(GamePhase.FINISHED, finalState.phase)
    }

    @Test
    fun test_revealNextCard_returnsNull_forInvalidSession() {
        val manager = GameManager()

        val result = manager.revealNextCard("invalid-id")

        // Invalid session should return null
        assertNull(result)
    }
}
