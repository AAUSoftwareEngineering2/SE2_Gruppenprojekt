package at.aau.kuhhandel.shared.websocket

import at.aau.kuhhandel.shared.model.GameState
import kotlinx.serialization.Serializable

/**
 * Payload used by CREATE_GAME commands
 */
@Serializable
data class CreateGamePayload(
    val playerName: String? = null,
)

/**
 * Payload used by GAME_CREATED events
 */
@Serializable
data class GameCreatedPayload(
    val gameId: String,
    val state: GameState,
)

/**
 * Payload used by GAME_STARTED and GAME_STATE_UPDATED events
 */
@Serializable
data class GameStatePayload(
    val state: GameState,
)

/**
 * Payload used by ERROR events
 */
@Serializable
data class ErrorPayload(
    val message: String,
)
