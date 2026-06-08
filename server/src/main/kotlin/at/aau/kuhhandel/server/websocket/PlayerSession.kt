package at.aau.kuhhandel.server.websocket

data class PlayerSession(
    val gameId: String,
    val playerId: String,
)
