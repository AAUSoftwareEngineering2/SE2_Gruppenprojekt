package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MoneyCard(
    val id: String,
    val value: Int,
)
