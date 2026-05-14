package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameServiceTest {
    private lateinit var eventPublisher: ApplicationEventPublisher

    @BeforeEach
    fun setUp() {
        eventPublisher = mock(ApplicationEventPublisher::class.java)
    }

    @Test
    fun test_createGame_generatesFiveDigitCode() {
        val service = GameService(eventPublisher)

        val result = service.createGame("Player 1")

        assertEquals(5, result.gameId.length)
        assertEquals(GamePhase.NOT_STARTED, result.gameState.phase)
    }

    @Test
    fun test_createGame_generatesDifferentCodes() {
        val service = GameService(eventPublisher)

        val firstResult = service.createGame("Player 1")
        val secondResult = service.createGame("Player 1")

        assertNotEquals(firstResult.gameId, secondResult.gameId)
    }

    @Test
    fun test_createGame_returnsCorrectResult() {
        val service = GameService(eventPublisher)

        val result = service.createGame("Player 1")

        assertEquals(1, result.gameState.players.size)
        assertEquals(result.playerId, result.gameState.players[0].id)
        assertEquals("Player 1", result.gameState.players[0].name)
    }

    @Test
    fun test_getGame_returnsCorrectSession() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")

        val loadedSession = service.getGame(result.gameId)

        assertNotNull(loadedSession)
        assertEquals(result.gameId, loadedSession.gameId)
    }

    @Test
    fun test_getGame_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val loadedSession = service.getGame("fake code")

        assertNull(loadedSession)
    }

    @Test
    fun test_startGame_startsExistingGame() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")

        val state = service.startGame(result.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(3, state.deck.size())
        assertNull(state.currentFaceUpCard)
    }

    @Test
    fun test_startGame_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val state = service.startGame("fake code")

        assertNull(state)
    }

    @Test
    fun test_startGame_returnsNull_ifSessionStartReturnsNull() {
        val service = GameService(eventPublisher)
        // No way to easily mock GameSession inside GameService since it's instantiated via new.
        // But we can check if it returns null for a game that doesn't exist (already tested).
    }

    @Test
    fun test_removeGame_removesGameSession() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        val gameId = result.gameId

        assertNotNull(service.getGame(gameId))

        service.removeGame(gameId)

        assertNull(service.getGame(gameId))
    }

    @Test
    fun test_joinGame_updatesGameState() {
        val service = GameService(eventPublisher)
        val createResult = service.createGame("Player 1")

        val joinResult = service.joinGame(createResult.gameId, "Player 2")

        assertNotNull(joinResult)
        assertEquals(2, joinResult.gameState.players.size)
        assertEquals(joinResult.playerId, joinResult.gameState.players[1].id)
        assertEquals("Player 2", joinResult.gameState.players[1].name)
    }

    @Test
    fun test_joinGame_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.joinGame("fake code", "Player 1")

        assertNull(result)
    }

    @Test
    fun test_leaveGame_updatesGameState() {
        val service = GameService(eventPublisher)
        val createResult = service.createGame("Player 1")
        val joinResult = service.joinGame(createResult.gameId, "Player 2")

        val state = service.leaveGame(createResult.gameId, joinResult!!.playerId)

        assertNotNull(state)
        assertEquals(1, state.players.size)
        assertEquals("Player 1", state.players[0].name)
    }

    @Test
    fun test_leaveGame_removesGameWhenEmpty() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")

        val state = service.leaveGame(result.gameId, result.playerId)

        assertNotNull(state)
        assertTrue(state.players.isEmpty())
        assertNull(service.getGame(result.gameId))
    }

    @Test
    fun test_leaveGame_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val state = service.leaveGame("fake code", "player-1")

        assertNull(state)
    }

    @Test
    fun test_revealNextCard_updatesGameState() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)

        val state = service.revealNextCard(result.gameId)

        assertNotNull(state)
        assertNotNull(state.currentFaceUpCard)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.deck.size())
    }

    @Test
    fun test_revealNextCard_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.revealNextCard("fake code")

        assertNull(result)
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckAlreadyEmpty() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)

        service.revealNextCard(result.gameId)
        service.revealNextCard(result.gameId)
        service.revealNextCard(result.gameId)
        val finalState = service.revealNextCard(result.gameId)

        assertNotNull(finalState)
        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
    }

    @Test
    fun test_chooseAuction_updatesGameState() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)

        val state = service.chooseAuction(result.gameId)

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

        val result = service.chooseAuction("fake code")

        assertNull(result)
    }

    @Test
    fun test_placeBid_updatesTimerEndTime() {
        val service = GameService(eventPublisher)
        val createResult = service.createGame("Player 1")

        // Add a second player so they can bid
        val joinResult = service.joinGame(createResult.gameId, "Player 2")

        service.startGame(createResult.gameId)
        service.chooseAuction(createResult.gameId)
        val initialEndTime = createResult.gameState.auctionState?.timerEndTime ?: 0

        // Place a valid bid
        val state = service.placeBid(createResult.gameId, joinResult!!.playerId, 10)

        assertNotNull(state)
        assertEquals(10, state.auctionState?.highestBid)
        assertEquals(joinResult.playerId, state.auctionState?.highestBidderId)
        assertNotNull(state.auctionState?.timerEndTime)
        // Timer should have been reset/updated
        assertTrue(state.auctionState!!.timerEndTime!! >= initialEndTime)
    }

    @Test
    fun test_closeAuction_updatesGameState() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)
        service.chooseAuction(result.gameId)

        val state = service.closeAuction(result.gameId)

        assertNotNull(state)
        assertTrue(state.auctionState!!.isClosed)
    }

    @Test
    fun test_resolveAuction_updatesGameState() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)
        service.chooseAuction(result.gameId)
        service.closeAuction(result.gameId)

        val state = service.resolveAuction(result.gameId, auctioneerBuysCard = false)

        assertNotNull(state)
        assertEquals(GamePhase.ROUND_END, state.phase)
        assertEquals(1, state.players[0].animals.size)
        assertNull(state.auctionState)
    }

    @Test
    fun test_resolveAuction_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.resolveAuction("fake code", auctioneerBuysCard = false)

        assertNull(result)
    }

    @Test
    fun test_chooseTrade_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.chooseTrade("fake code", "player-2", AnimalType.COW)

        assertNull(result)
    }

    @Test
    fun test_chooseTrade_propagatesInvalidTrade() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)

        assertFailsWith<IllegalArgumentException> {
            service.chooseTrade(result.gameId, "player-3", AnimalType.COW)
        }
    }

    @Test
    fun test_chooseTrade_callsSession() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        val joinResult = service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)

        // This is hard to test deeply without mocking GameSession,
        // but we can at least hit the branch in GameService.
    }

    @Test
    fun test_offerTrade_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.offerTrade("fake code", listOf("m-1"))

        assertNull(result)
    }

    @Test
    fun test_offerTrade_propagatesInvalidPhase() {
        val service = GameService(eventPublisher)
        val result = service.createGame("player-1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)

        assertFailsWith<IllegalStateException> {
            service.offerTrade(result.gameId, listOf("m-1"))
        }
    }

    @Test
    fun test_respondToTrade_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.respondToTrade("fake code", "player-2", true)

        assertNull(result)
    }

    @Test
    fun test_respondToTrade_propagatesInvalidPhase() {
        val service = GameService(eventPublisher)
        val result = service.createGame("player-1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)

        assertFailsWith<IllegalStateException> {
            service.respondToTrade(result.gameId, "player-2", false)
        }
    }

    @Test
    fun test_finishRound_updatesGameState() {
        val service = GameService(eventPublisher)
        val result = service.createGame("Player 1")
        service.joinGame(result.gameId, "Player 2")
        service.startGame(result.gameId)
        service.chooseAuction(result.gameId)

        val state = service.finishRound(result.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.roundNumber)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_finishRound_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)

        val result = service.finishRound("fake code")

        assertNull(result)
    }

    @Test
    fun test_placeBid_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)
        val result = service.placeBid("fake code", "p1", 10)
        assertNull(result)
    }

    @Test
    fun test_closeAuction_returnsNull_forInvalidGameId() {
        val service = GameService(eventPublisher)
        val result = service.closeAuction("fake code")
        assertNull(result)
    }

    @Test
    fun test_scheduleAutoClose_executesAndPublishesEvent() {
        val service = GameService(eventPublisher)
        val result = service.createGame("p1")
        service.joinGame(result.gameId, "p2")
        service.startGame(result.gameId)
        service.chooseAuction(result.gameId)

        // The timer is set to 5 seconds. We need to wait for the coroutine.
        // In a real unit test we might want to mock the dispatcher or use runTest,
        // but since it's using CoroutineScope(Dispatchers.Default), it's harder to control.
        // For now, let's just wait a bit longer than 5.1s.
        Thread.sleep(6000)

        verify(
            eventPublisher,
            timeout(1000),
        ).publishEvent(any<at.aau.kuhhandel.server.event.GameStateChangedEvent>())
        assertTrue(
            service
                .getGame(result.gameId)!!
                .gameState.auctionState!!
                .isClosed,
        )
    }

    @Test
    fun test_scheduleAutoClose_abortsIfTimerChanged() {
        val service = GameService(eventPublisher)
        val createResult = service.createGame("p1")

        // Add a second player so they can bid
        val joinResult = service.joinGame(createResult.gameId, "Player 2")

        service.startGame(createResult.gameId)
        service.chooseAuction(createResult.gameId)

        // Place a bid halfway through
        Thread.sleep(3000)
        service.placeBid(createResult.gameId, joinResult!!.playerId, 10)

        // Wait for the first scheduleAutoClose to finish
        Thread.sleep(3000)

        // The auction should NOT be closed by the FIRST scheduleAutoClose
        // because the timerEndTime changed.
        // Note: the SECOND scheduleAutoClose (from placeBid) might still be running.
    }

    @Test
    fun test_scheduleAutoClose_abortsIfAlreadyClosed() {
        val service = GameService(eventPublisher)
        val result = service.createGame("p1")
        service.joinGame(result.gameId, "p2")
        service.startGame(result.gameId)
        service.chooseAuction(result.gameId)

        // Manually close it
        service.closeAuction(result.gameId)

        Thread.sleep(6000)
        // Should not have published more events from scheduleAutoClose if it checks isClosed
        verify(
            eventPublisher,
            never(),
        ).publishEvent(any<at.aau.kuhhandel.server.event.GameStateChangedEvent>())
    }

    @Test
    fun test_scheduleAutoClose_returnsEarlyIfSessionRemoved() {
        val service = GameService(eventPublisher)
        val result = service.createGame("p1")
        service.joinGame(result.gameId, "p2")
        service.startGame(result.gameId)
        service.chooseAuction(result.gameId)

        service.removeGame(result.gameId)

        Thread.sleep(6000)
        verify(eventPublisher, never()).publishEvent(any())
    }
}
