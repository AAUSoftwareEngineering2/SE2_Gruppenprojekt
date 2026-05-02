package at.aau.kuhhandel.app.ui

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable
    data object Main : Screen()

    @Serializable
    data object RoomCreation : Screen()

    @Serializable
    data object RoomJoining : Screen()

    @Serializable
    data class Lobby(
        val lobbyCode: String,
    ) : Screen()

    @Serializable
    data object Rules : Screen()

    @Serializable
    data object Game : Screen()

    @Serializable
    data object GamePrototype : Screen()
}
