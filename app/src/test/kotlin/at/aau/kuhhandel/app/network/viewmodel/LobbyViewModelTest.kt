package at.aau.kuhhandel.app.network.viewmodel

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameRepositoryState
import at.aau.kuhhandel.app.ui.menu.lobby.LobbyViewModel
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
class LobbyViewModelTest {
    // Specialized coroutine dispatcher used for unit testings
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher) // coroutine scope
    private val mockRepository = mockk<GameRepository>(relaxed = true)
    private val repoStateFlow = MutableStateFlow(GameRepositoryState())

    private lateinit var viewModel: LobbyViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.state } returns repoStateFlow
        viewModel = LobbyViewModel(mockRepository, testScope, "12345")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects initial set lobby code`() {
        runTest {
            val uiState = viewModel.uiState.value
            assertEquals("12345", uiState.lobbyCode)
            assertEquals("Not connected", uiState.connectionStatus)
        }
    }

    @Test
    fun `the state updates when repository state changes`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameId = "ABCDE",
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals("ABCDE", uiState.lobbyCode)
            assertEquals("Connected", uiState.connectionStatus)
        }
    }

    @Test
    fun `all players are mapped correctly from game state`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            val players =
                listOf(
                    PlayerState(id = "p1", name = "Alice"),
                    PlayerState(id = "p2", name = "Bob"),
                )
            val gameState = GameState(players = players, phase = GamePhase.NOT_STARTED)

            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = gameState,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(2, uiState.players.size)
            assertEquals("Alice", uiState.players[0].name)
            assertTrue(uiState.players[0].isHost)
            assertEquals("Bob", uiState.players[1].name)
            assertFalse(uiState.players[1].isHost)
        }
    }

    @Test
    fun `canStartGame is true only when host and connected and not started yet`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // case one, connecte no game state yet (waiting for creation)
            repoStateFlow.value = GameRepositoryState(isConnected = true)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canStartGame)

            // Case two, Game already started
            val startedGameState = GameState(phase = GamePhase.AUCTION)
            repoStateFlow.value =
                GameRepositoryState(isConnected = true, gameState = startedGameState)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canStartGame)

            // disconnected
            repoStateFlow.value = GameRepositoryState(isConnected = false)
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
    fun `clearError calls repository`() {
        runTest {
            viewModel.clearError()
            advanceUntilIdle()
            verify { mockRepository.clearError() }
        }
    }
}
