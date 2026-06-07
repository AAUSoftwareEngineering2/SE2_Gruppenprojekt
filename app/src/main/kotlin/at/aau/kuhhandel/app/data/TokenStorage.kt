package at.aau.kuhhandel.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TokenStorage(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            "kuhhandel_prefs",
            Context.MODE_PRIVATE,
        )

    companion object {
        private const val KEY_GAME_ID = "game_id"
        private const val KEY_PLAYER_ID = "player_id"
        private const val KEY_RECONNECT_TOKEN = "reconnect_token"
    }

    /**
     * Saves all details of the current game session.
     */
    fun saveSession(
        gameId: String,
        playerId: String,
        token: String,
    ) {
        prefs.edit {
            putString(KEY_GAME_ID, gameId)
            putString(KEY_PLAYER_ID, playerId)
            putString(KEY_RECONNECT_TOKEN, token)
        }
    }

    /**
     * Updates the reconnect token.
     */
    fun saveReconnectToken(token: String) {
        prefs.edit {
            putString(KEY_RECONNECT_TOKEN, token)
        }
    }

    fun getGameId(): String? = prefs.getString(KEY_GAME_ID, null)

    fun getPlayerId(): String? = prefs.getString(KEY_PLAYER_ID, null)

    fun getReconnectToken(): String? = prefs.getString(KEY_RECONNECT_TOKEN, null)

    /**
     * Wipes the current session data.
     */
    fun clearSession() {
        prefs.edit {
            remove(KEY_GAME_ID)
            remove(KEY_PLAYER_ID)
            remove(KEY_RECONNECT_TOKEN)
        }
    }
}
