package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.shared.model.GameState

data class RoomActionResult(
    val gameId: String,
    val playerId: String,
    val gameState: GameState,
)
