package at.aau.kuhhandel.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenStorageTest {
    private lateinit var context: Context
    private lateinit var tokenStorage: TokenStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tokenStorage = TokenStorage(context)
    }

    @Test
    fun `saveSession stores all values correctly`() {
        tokenStorage.saveSession(
            gameId = "game-1",
            playerId = "player-1",
            token = "token-1",
        )

        assertEquals("game-1", tokenStorage.getGameId())
        assertEquals("player-1", tokenStorage.getPlayerId())
        assertEquals("token-1", tokenStorage.getReconnectToken())
    }

    @Test
    fun `saveReconnectToken updates reconnect token`() {
        tokenStorage.saveSession(
            gameId = "game-1",
            playerId = "player-1",
            token = "old-token",
        )

        tokenStorage.saveReconnectToken("new-token")

        assertEquals("game-1", tokenStorage.getGameId())
        assertEquals("player-1", tokenStorage.getPlayerId())
        assertEquals("new-token", tokenStorage.getReconnectToken())
    }

    @Test
    fun `clearSession wipes all stored values`() {
        tokenStorage.saveSession(
            gameId = "game-1",
            playerId = "player-1",
            token = "token-1",
        )

        tokenStorage.clearSession()

        assertNull(tokenStorage.getGameId())
        assertNull(tokenStorage.getPlayerId())
        assertNull(tokenStorage.getReconnectToken())
    }
}
