package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable

@Serializable
data class CreateGamePayload(
    val playerName: String? = null,
)

@Serializable
data class ErrorPayload(
    val message: String,
)
