package at.aau.kuhhandel.shared.model

data class PlayerState (
    val id: String,
    val name: String,
    val animals: List<AnimalCard> = emptyList(), // Empty List as animals are aquired troughout the game
    val moneyCards: List<MoneyCard> = emptyList()
) {
    fun totalMoney(): Int = moneyCards.sumOf {it.value} //helper method to get the total sum of money by the cards the player is holding
}
