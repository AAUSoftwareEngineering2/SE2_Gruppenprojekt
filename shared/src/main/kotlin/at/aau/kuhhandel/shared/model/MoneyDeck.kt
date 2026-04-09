package at.aau.kuhhandel.shared.model

class MoneyDeck {

    val cards: MutableList<MoneyCard> = mutableListOf()

    init {
        generateDeck()
        cards.shuffle()
    }

    private fun generateDeck() {
        repeat(10) { cards.add(MoneyCard("0-$it", 0)) }
        repeat(25) { cards.add(MoneyCard("10-$it", 10)) }
        repeat(5) { cards.add(MoneyCard("50-$it", 50)) }
        repeat(5) { cards.add(MoneyCard("100-$it", 100)) }
        repeat(5) { cards.add(MoneyCard("200-$it", 200)) }
        repeat(5) { cards.add(MoneyCard("500-$it", 500)) }
    }
}
