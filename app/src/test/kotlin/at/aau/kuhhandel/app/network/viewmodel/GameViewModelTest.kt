package at.aau.kuhhandel.app.network.viewmodel

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameRepositoryState
import at.aau.kuhhandel.app.ui.game.GameViewModel
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockRepository = mockk<GameRepository>(relaxed = true)
    private val repoStateFlow = MutableStateFlow(GameRepositoryState())
    private val mockTimeProvider =
        object : at.aau.kuhhandel.app.ui.game.TimeProvider {
            override fun currentTimeMillis(): Long = testDispatcher.scheduler.currentTime
        }

    private lateinit var viewModel: GameViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.state } returns repoStateFlow
        viewModel = GameViewModel(mockRepository, testScope, mockTimeProvider)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() {
        runTest {
            val uiState = viewModel.uiState.value
            assertEquals(GamePhase.NOT_STARTED, uiState.currentPhase)
            assertEquals("0 cards left", uiState.deckCountText)
            assertEquals("No card revealed", uiState.activeCardLabel)
            assertFalse(uiState.isConnected)
        }
    }

    @Test
    fun `state updates from repository correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            val deck = AnimalDeck(listOf(AnimalCard("1", AnimalType.COW)))
            val gameState =
                GameState(
                    phase = GamePhase.PLAYER_TURN,
                    deck = deck,
                    currentFaceUpCard = AnimalCard("2", AnimalType.PIG),
                )

            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = gameState,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(GamePhase.PLAYER_TURN, uiState.currentPhase)
            assertEquals("1 cards left", uiState.deckCountText)
            assertEquals("PIG (#2)", uiState.activeCardLabel)
            assertTrue(uiState.isConnected)
            assertTrue(uiState.canRevealCard)
            assertFalse(uiState.canStartGame)
        }
    }

    @Test
    fun `canRevealCard depends on connection and phase`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Case 1: Connected + PLAYER_TURN + My Turn -> True
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    myPlayerId = "me",
                    gameState =
                        GameState(
                            phase = GamePhase.PLAYER_TURN,
                            players = listOf(PlayerState(id = "me", name = "Me")),
                            currentPlayerIndex = 0,
                        ),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canRevealCard)

            // Case 2: Connected + AUCTION -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = GameState(phase = GamePhase.AUCTION),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canRevealCard)

            // Case 3: Disconnected + PLAYER_TURN -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = false,
                    gameState = GameState(phase = GamePhase.PLAYER_TURN),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canRevealCard)
        }
    }

    @Test
    fun `isMyTurn logic works correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            val players =
                listOf(
                    PlayerState(id = "player-1", name = "Me"),
                    PlayerState(id = "player-2", name = "Opponent"),
                )

            // Case 1: My turn
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "player-1",
                    gameState = GameState(players = players, currentPlayerIndex = 0),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isMyTurn)

            // Case 2: Not my turn
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "player-1",
                    gameState = GameState(players = players, currentPlayerIndex = 1),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isMyTurn)
            assertEquals("Opponent", viewModel.uiState.value.activePlayerName)

            // Case 2: Opponent turn
            repoStateFlow.value =
                GameRepositoryState(
                    gameState = GameState(players = players, currentPlayerIndex = 1),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isMyTurn)
            assertEquals("Opponent", viewModel.uiState.value.activePlayerName)
        }
    }

    @Test
    fun `canStartGame logic works correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Case 1: Connected + NOT_STARTED -> True
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = GameState(phase = GamePhase.NOT_STARTED),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canStartGame)

            // Case 2: Connected + PLAYER_TURN -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = GameState(phase = GamePhase.PLAYER_TURN),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canStartGame)
        }
    }

    @Test
    fun `startGame calls repository`() {
        runTest {
            viewModel.startGame()
            advanceUntilIdle()
            coVerify { mockRepository.startGame() }
        }
    }

    @Test
    fun `revealCard calls repository`() {
        runTest {
            viewModel.revealCard()
            advanceUntilIdle()
            coVerify { mockRepository.revealCard() }
        }
    }

    @Test
    fun `placeBid calls repository and handles error`() {
        runTest {
            coEvery { mockRepository.placeBid(50) } throws RuntimeException("Network error")
            viewModel.placeBid(50)
            advanceUntilIdle()
            coVerify { mockRepository.placeBid(50) }
        }
    }

    @Test
    fun `buyBack calls repository and handles error`() {
        runTest {
            coEvery { mockRepository.buyBack(true) } throws RuntimeException("Network error")
            viewModel.buyBack(true)
            advanceUntilIdle()
            coVerify { mockRepository.buyBack(true) }
        }
    }

    @Test
    fun `initiateTrade calls repository and handles error`() {
        runTest {
            coEvery { mockRepository.initiateTrade("player-2") } throws
                RuntimeException("Network error")
            viewModel.initiateTrade("player-2")
            advanceUntilIdle()
            coVerify { mockRepository.initiateTrade("player-2") }
        }
    }

    @Test
    fun `auction timer updates correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Set endTime to be 5 seconds in the future relative to current scheduler time
            val endTime = testScheduler.currentTime + 5000
            val gameState =
                GameState(
                    phase = GamePhase.AUCTION,
                    auctionState =
                        AuctionState(
                            auctioneerId = "p1",
                            auctionCard = AnimalCard("1", AnimalType.COW),
                            timerEndTime = endTime,
                        ),
                    players = listOf(PlayerState(id = "p1", name = "P1")),
                )

            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = gameState,
                )

            // No advanceUntilIdle() here as it might loop through the 250ms delays
            advanceTimeBy(1) // Trigger initial emission

            // Check initial timer value
            assertEquals(5, viewModel.uiState.value.auctionTimerSeconds)

            // Advance time and check again
            advanceTimeBy(2000)
            assertEquals(3, viewModel.uiState.value.auctionTimerSeconds)

            // Advance past end time
            advanceTimeBy(3000)
            assertEquals(0, viewModel.uiState.value.auctionTimerSeconds)
        }
    }

    @Test
    fun `auction timer is null when no auction is active`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            repoStateFlow.value =
                GameRepositoryState(
                    gameState = GameState(phase = GamePhase.PLAYER_TURN),
                )
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.auctionTimerSeconds)
        }
    }
}
