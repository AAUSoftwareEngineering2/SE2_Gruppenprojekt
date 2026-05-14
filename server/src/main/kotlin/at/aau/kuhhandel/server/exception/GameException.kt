package at.aau.kuhhandel.server.exception

import at.aau.kuhhandel.shared.enums.GameErrorReason

class GameException(
    val reason: GameErrorReason,
) : RuntimeException()
