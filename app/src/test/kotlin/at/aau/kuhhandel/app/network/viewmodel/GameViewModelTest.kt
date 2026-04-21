package at.aau.kuhhandel.app.network.viewmodel

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameRepositoryState
import at.aau.kuhhandel.app.ui.game.GameViewModel
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.GameState
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockRepository = mockk<GameRepository>(relaxed = true)
    private val repoStateFlow = MutableStateFlow(GameRepositoryState())

    private lateinit var viewModel: GameViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.state } returns repoStateFlow
        viewModel = GameViewModel(mockRepository, testScope)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val uiState = viewModel.uiState.value
        assertEquals(GamePhase.NOT_STARTED, uiState.currentPhase)
        assertEquals("0 cards left", uiState.deckCountText)
        assertEquals("No card revealed", uiState.activeCardLabel)
        assertFalse(uiState.isConnected)
    }

    @Test
    fun `state updates from repository correctly`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val deck = AnimalDeck(listOf(AnimalCard("1", AnimalType.COW)))
        val gameState = GameState(
            phase = GamePhase.PLAYER_TURN,
            deck = deck,
            currentFaceUpCard = AnimalCard("2", AnimalType.PIG)
        )

        repoStateFlow.value = GameRepositoryState(
            isConnected = true,
            gameState = gameState
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


    @Test
    fun `canRevealCard depends on connection and phase`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Case 1: Connected + PLAYER_TURN -> True
        repoStateFlow.value = GameRepositoryState(
            isConnected = true,
            gameState = GameState(phase = GamePhase.PLAYER_TURN)
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canRevealCard)

        // Case 2: Connected + AUCTION -> False
        repoStateFlow.value = GameRepositoryState(
            isConnected = true,
            gameState = GameState(phase = GamePhase.AUCTION)
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canRevealCard)

        // Case 3: Disconnected + PLAYER_TURN -> False
        repoStateFlow.value = GameRepositoryState(
            isConnected = false,
            gameState = GameState(phase = GamePhase.PLAYER_TURN)
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canRevealCard)
    }

    @Test
    fun `startGame calls repository`() = runTest {
        viewModel.startGame()
        advanceUntilIdle()
        coVerify { mockRepository.startGame() }
    }

    @Test
    fun `revealCard calls repository`() = runTest {
        viewModel.revealCard()
        advanceUntilIdle()
        coVerify { mockRepository.revealCard() }
    }
}
