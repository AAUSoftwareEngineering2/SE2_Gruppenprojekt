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
    fun `initial state is fully correct`() = runTest {
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isConnecting)
        assertNull(uiState.gameId)
        assertNull(uiState.errorMessage)
        assertFalse(uiState.isCreated)
    }

    @Test
    fun `createLobby shall call repository`() = runTest {
        viewModel.createLobby()
        advanceUntilIdle()
        coVerify { mockRepository.createGame() }
    }

    @Test
    fun `state reflects repository updates`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        repoStateFlow.value = GameRepositoryState(
            isConnecting = true
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isConnecting)

        repoStateFlow.value = GameRepositoryState(
            gameId = "ABCDE",
            isConnecting = false
        )
        advanceUntilIdle()
        assertEquals("ABCDE", viewModel.uiState.value.gameId)
        assertTrue(viewModel.uiState.value.isCreated)

        repoStateFlow.value = GameRepositoryState(
            errorMessage = "Error occurred"
        )
        advanceUntilIdle()
        assertEquals("Error occurred", viewModel.uiState.value.errorMessage)
    }
}
