package at.aau.kuhhandel.shared.model

class MoneyDeck {
    val cards: MutableList<MoneyCard> = mutableListOf()

    init {
        generateDeck()
        cards.shuffle()
    }

    private fun generateDeck() {
        val distribution =
            mapOf(
                0 to 10,
                10 to 25,
                50 to 5,
                100 to 5,
                200 to 5,
                500 to 5,
            )

        distribution.forEach { (value, amount) ->
            repeat(amount) { index ->
                cards.add(
                    MoneyCard(
                        id = "$value-$index",
                        value = value,
                    ),
                )
            }
        }
    }
}
