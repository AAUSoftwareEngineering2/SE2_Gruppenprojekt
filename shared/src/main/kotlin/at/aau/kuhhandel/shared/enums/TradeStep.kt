package at.aau.kuhhandel.shared.enums

import kotlinx.serialization.Serializable

@Serializable
enum class TradeStep {
    WAITING_FOR_RESPONSE,
    RESOLVED,
}
