package at.aau.kuhhandel.app.network.viewmodel

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameRepositoryState
import at.aau.kuhhandel.app.ui.menu.creation.LobbyCreationViewModel
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LobbyCreationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockRepository = mockk<GameRepository>(relaxed = true)
    private val repoStateFlow = MutableStateFlow(GameRepositoryState())

    private lateinit var viewModel: LobbyCreationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.state } returns repoStateFlow
        viewModel = LobbyCreationViewModel(mockRepository, testScope)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is fully correct`() {
        runTest {
            val uiState = viewModel.uiState.value
            assertEquals("", uiState.playerName)
            assertNull(uiState.playerNameError)
            assertFalse(uiState.isConnecting)
            assertNull(uiState.gameId)
            assertNull(uiState.errorMessage)
            assertFalse(uiState.isCreated)
            assertFalse(uiState.canSubmit)
        }
    }

    @Test
    fun `onPlayerNameChanged updates name and clears error`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onPlayerNameChanged("Felix")
            advanceUntilIdle()
            assertEquals("Felix", viewModel.uiState.value.playerName)
            assertTrue(viewModel.uiState.value.canSubmit)
        }
    }

    @Test
    fun `onPlayerNameChanged ignores input above max length`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onPlayerNameChanged("Felix1234") // 9 chars
            advanceUntilIdle()
            assertEquals("", viewModel.uiState.value.playerName)
        }
    }

    @Test
    fun `createLobby with valid name calls repository`() {
        runTest {
            viewModel.onPlayerNameChanged("Felix01")
            viewModel.createLobby()
            advanceUntilIdle()
            coVerify { mockRepository.createGame("Felix01") }
        }
    }

    @Test
    fun `createLobby with empty name surfaces validation error and skips repository`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.createLobby()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.playerNameError)
            coVerify(exactly = 0) { mockRepository.createGame(any()) }
        }
    }

    @Test
    fun `createLobby rejects invalid characters`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onPlayerNameChanged("Fe lix")
            // length is 6 but contains a space — invalid
            viewModel.createLobby()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.playerNameError)
            coVerify(exactly = 0) { mockRepository.createGame(any()) }
        }
    }

    @Test
    fun `state reflects repository updates`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            repoStateFlow.value =
                GameRepositoryState(
                    isConnecting = true,
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isConnecting)

            repoStateFlow.value =
                GameRepositoryState(
                    gameId = "ABCDE",
                    gameState = mockk(),
                    isConnecting = false,
                )
            advanceUntilIdle()
            assertEquals("ABCDE", viewModel.uiState.value.gameId)
            assertTrue(viewModel.uiState.value.isCreated)
            assertFalse(viewModel.uiState.value.isCreating)

            repoStateFlow.value =
                GameRepositoryState(
                    errorMessage = "Error occurred",
                )
            advanceUntilIdle()
            assertEquals("Error occurred", viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isCreating)
        }
    }

    @Test
    fun `isCreating bridges the gap during creation`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onPlayerNameChanged("Felix")
            advanceUntilIdle()

            viewModel.createLobby()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isCreating)

            // Simulate server response
            repoStateFlow.value =
                GameRepositoryState(
                    gameId = "12345",
                    gameState = mockk(),
                )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isCreating)
            assertTrue(viewModel.uiState.value.isCreated)
        }
    }
}
