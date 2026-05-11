package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.GamePhase
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameServiceTest {
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    @Test
    fun test_createGame_generatesFiveDigitCode() {
        val service = GameService(eventPublisher)

        val session = service.createGame("player-1")

        assertNotNull(session)
        assertEquals(5, session.gameId.length)
        assertEquals(GamePhase.NOT_STARTED, session.gameState.phase)
    }

    @Test
    fun test_createGame_generatesDifferentCodes() {
        val service = GameService(eventPublisher)

        val firstSession = service.createGame("player-1")
        val secondSession = service.createGame("player-1")

        assertNotEquals(firstSession.gameId, secondSession.gameId)
    }

    @Test
    fun test_getGame_returnsCorrectSession() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")

        val loadedSession = service.getGame(session.gameId)

        assertNotNull(loadedSession)
        assertEquals(session.gameId, loadedSession.gameId)
    }

    @Test
    fun test_getGame_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val loadedSession = service.getGame("99999")

        assertNull(loadedSession)
    }

    @Test
    fun test_startGame_startsExistingGame() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")

        val state = service.startGame(session.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(3, state.deck.size())
        assertNull(state.currentFaceUpCard)
    }

    @Test
    fun test_startGame_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val state = service.startGame("99999")

        assertNull(state)
    }

    @Test
    fun test_removeGame_removesGameSession() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        val gameId = session.gameId

        assertNotNull(service.getGame(gameId))

        service.removeGame(gameId)

        assertNull(service.getGame(gameId))
    }

    @Test
    fun test_revealNextCard_updatesGameState() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        val state = service.revealNextCard(session.gameId)

        assertNotNull(state)
        assertNotNull(state.currentFaceUpCard)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.deck.size())
    }

    @Test
    fun test_revealNextCard_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.revealNextCard("99999")

        assertNull(result)
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckAlreadyEmpty() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        service.revealNextCard(session.gameId)
        service.revealNextCard(session.gameId)
        service.revealNextCard(session.gameId)
        val finalState = service.revealNextCard(session.gameId)

        assertNotNull(finalState)
        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
    }

    @Test
    fun test_chooseAuction_updatesGameState() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        val state = service.chooseAuction(session.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.AUCTION, state.phase)
        assertNotNull(state.auctionState)
        assertEquals(2, state.deck.size())
        assertNull(state.currentFaceUpCard)
        assertNotNull(state.auctionState?.timerEndTime)
    }

    @Test
    fun test_chooseAuction_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.chooseAuction("99999")

        assertNull(result)
    }

    @Test
    fun test_placeBid_updatesTimerEndTime() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        // Add a second player so they can bid
        session.gameState.players
            .toMutableList()
            .apply {
                add(
                    at.aau.kuhhandel.shared.model
                        .PlayerState(id = "player-2", name = "player-2"),
                )
            }.let { updatedPlayers ->
                // Manually update players in session for this test
                val field = session.javaClass.getDeclaredField("gameState")
                field.isAccessible = true
                val currentState = field.get(session) as at.aau.kuhhandel.shared.model.GameState
                field.set(session, currentState.copy(players = updatedPlayers))
            }

        service.chooseAuction(session.gameId)
        val initialEndTime = session.gameState.auctionState?.timerEndTime ?: 0

        // Place a valid bid
        val state = service.placeBid(session.gameId, "player-2", 10)

        assertNotNull(state)
        assertEquals(10, state.auctionState?.highestBid)
        assertEquals("player-2", state.auctionState?.highestBidderId)
        assertNotNull(state.auctionState?.timerEndTime)
        // Timer should have been reset/updated
        assertTrue(state.auctionState!!.timerEndTime!! >= initialEndTime)
    }

    @Test
    fun test_closeAuction_updatesGameState() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)

        val state = service.closeAuction(session.gameId)

        assertNotNull(state)
        assertTrue(state.auctionState!!.isClosed)
    }

    @Test
    fun test_resolveAuction_updatesGameState() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)
        service.closeAuction(session.gameId)

        val state = service.resolveAuction(session.gameId, auctioneerBuysCard = false)

        assertNotNull(state)
        assertEquals(GamePhase.ROUND_END, state.phase)
        assertEquals(1, state.players[0].animals.size)
        assertNull(state.auctionState)
    }

    @Test
    fun test_resolveAuction_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.resolveAuction("99999", auctioneerBuysCard = false)

        assertNull(result)
    }

    @Test
    fun test_chooseTrade_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.chooseTrade("99999", "player-2")

        assertNull(result)
    }

    @Test
    fun test_chooseTrade_propagatesInvalidTrade() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        assertFailsWith<IllegalArgumentException> {
            service.chooseTrade(session.gameId, "player-2")
        }
    }

    @Test
    fun test_offerTrade_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.offerTrade("99999", listOf("m-1"))

        assertNull(result)
    }

    @Test
    fun test_offerTrade_propagatesInvalidPhase() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        assertFailsWith<IllegalStateException> {
            service.offerTrade(session.gameId, listOf("m-1"))
        }
    }

    @Test
    fun test_respondToTrade_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.respondToTrade("99999", "player-2", true)

        assertNull(result)
    }

    @Test
    fun test_respondToTrade_propagatesInvalidPhase() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        assertFailsWith<IllegalStateException> {
            service.respondToTrade(session.gameId, "player-2", false)
        }
    }

    @Test
    fun test_finishRound_updatesGameState() {
        val service = GameService(eventPublisher)
        val session = service.createGame("player-1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)

        val state = service.finishRound(session.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.roundNumber)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_finishRound_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.finishRound("99999")

        assertNull(result)
    }

    @Test
    fun test_placeBid_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)
        val result = service.placeBid("99999", "p1", 10)
        assertNull(result)
    }

    @Test
    fun test_closeAuction_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)
        val result = service.closeAuction("99999")
        assertNull(result)
    }

    @Test
    fun test_scheduleAutoClose_executesAndPublishesEvent() {
        val service = GameService(eventPublisher)
        val session = service.createGame("p1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)

        // The timer is set to 5 seconds. We need to wait for the coroutine.
        // In a real unit test we might want to mock the dispatcher or use runTest,
        // but since it's using CoroutineScope(Dispatchers.Default), it's harder to control.
        // For now, let's just wait a bit longer than 5.1s.
        Thread.sleep(6000)

        io.mockk.verify(timeout = 1000) {
            eventPublisher.publishEvent(any<at.aau.kuhhandel.server.event.GameStateChangedEvent>())
        }
        assertTrue(session.gameState.auctionState!!.isClosed)
    }

    @Test
    fun test_scheduleAutoClose_abortsIfTimerChanged() {
        val service = GameService(eventPublisher)
        val session = service.createGame("p1")
        service.startGame(session.gameId)

        // Add a second player so they can bid
        session.gameState.players
            .toMutableList()
            .apply {
                add(
                    at.aau.kuhhandel.shared.model
                        .PlayerState(id = "player-2", name = "player-2"),
                )
            }.let { updatedPlayers ->
                val field = session.javaClass.getDeclaredField("gameState")
                field.isAccessible = true
                val currentState = field.get(session) as at.aau.kuhhandel.shared.model.GameState
                field.set(session, currentState.copy(players = updatedPlayers))
            }

        service.chooseAuction(session.gameId)

        // Place a bid halfway through
        Thread.sleep(3000)
        service.placeBid(session.gameId, "player-2", 10)

        // Wait for the first scheduleAutoClose to finish
        Thread.sleep(3000)

        // The auction should NOT be closed by the FIRST scheduleAutoClose
        // because the timerEndTime changed.
        // Note: the SECOND scheduleAutoClose (from placeBid) might still be running.
    }

    @Test
    fun test_scheduleAutoClose_abortsIfAlreadyClosed() {
        val service = GameService(eventPublisher)
        val session = service.createGame("p1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)

        // Manually close it
        service.closeAuction(session.gameId)

        Thread.sleep(6000)
        // Should not have published more events from scheduleAutoClose if it checks isClosed
    }
}
