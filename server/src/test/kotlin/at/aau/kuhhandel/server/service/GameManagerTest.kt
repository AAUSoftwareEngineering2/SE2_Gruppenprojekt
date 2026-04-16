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

        assertEquals(GamePhase.PLAYER_TURN, session.gameState.phase)
        assertNull(session.gameState.currentFaceUpCard)
        assertEquals(3, session.gameState.deck.size())
    }

    @Test
    fun test_getSession_returnsCorrectSession() {
        val manager = GameManager()
        val session = manager.createTestGame()

        val loadedSession = manager.getSession(session.sessionId)

        assertNotNull(loadedSession)
        assertEquals(session.sessionId, loadedSession.sessionId)
    }

    @Test
    fun test_revealNextCard_revealsCard() {
        val manager = GameManager()
        val session = manager.createTestGame()

        val updatedState = manager.revealNextCard(session.sessionId)

        assertNotNull(updatedState)
        assertNotNull(updatedState.currentFaceUpCard)
        assertEquals(GamePhase.PLAYER_TURN, updatedState.phase)
    }

    @Test
    fun test_revealNextCard_reducesDeckSize() {
        val manager = GameManager()
        val session = manager.createTestGame()

        val beforeSize = session.gameState.deck.size()
        val updatedState = manager.revealNextCard(session.sessionId)
        val afterSize = updatedState?.deck?.size()

        assertEquals(beforeSize - 1, afterSize)
    }

    @Test
    fun test_revealNextCard_updatesStoredSessionState() {
        val manager = GameManager()
        val session = manager.createTestGame()

        manager.revealNextCard(session.sessionId)
        val loadedSession = manager.getSession(session.sessionId)

        assertNotNull(loadedSession)
        assertNotNull(loadedSession.gameState.currentFaceUpCard)
        assertEquals(2, loadedSession.gameState.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, loadedSession.gameState.phase)
    }

    @Test
    fun test_revealNextCard_lastCardDoesNotImmediatelyFinishGame() {
        val manager = GameManager()
        val session = manager.createTestGame()

        manager.revealNextCard(session.sessionId)
        manager.revealNextCard(session.sessionId)
        val stateAfterLastCard = manager.revealNextCard(session.sessionId)

        assertNotNull(stateAfterLastCard)
        assertNotNull(stateAfterLastCard.currentFaceUpCard)
        assertEquals(GamePhase.PLAYER_TURN, stateAfterLastCard.phase)
        assertEquals(0, stateAfterLastCard.deck.size())
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckAlreadyEmpty() {
        val manager = GameManager()
        val session = manager.createTestGame()

        manager.revealNextCard(session.sessionId)
        manager.revealNextCard(session.sessionId)
        manager.revealNextCard(session.sessionId)
        val finalState = manager.revealNextCard(session.sessionId)

        assertNotNull(finalState)
        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
    }

    @Test
    fun test_revealNextCard_returnsNull_forInvalidSession() {
        val manager = GameManager()

        val result = manager.revealNextCard("invalid-id")

        assertNull(result)
    }
}
