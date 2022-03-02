package com.example.mymemory.models

import com.example.mymemory.utils.DEFAULT_ICONS
// memory game class，用来包含所有东西
class MemoryGame(private val boardSize: BoardSize) {

    val cards: List<MemoryCard>
    var numsPairsFound = 0

    private var numCardFlip = 0
    private var indexOfSingleSelectedCard: Int? = null

    init{
        // 从list 里面取出对应的image
        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getPairs())
        val randomizedImages = (chosenImages + chosenImages).shuffled()
        // 把randomizedImages每一个元素转换成memory card class
        cards = randomizedImages.map{ MemoryCard(it) }
    }

    fun flipCard(position: Int): Boolean {
        val card = cards[position]
        numCardFlip++
        // 有三种情况
        // 没有卡片在之前被翻 -> restore + flip over selected card
        // 一个卡片在之前被翻 -> flip over selected card + check if the images match
        // 两个卡片在之前被翻 -> restore + flip over selected card
        // 如果indexOfSingleSelectedCard是null的话，说明刚翻第一个卡片
        var foundMatch = false
        if (indexOfSingleSelectedCard == null) {
            restore()
            indexOfSingleSelectedCard = position
        } else {
            // one card previously flip over
            foundMatch = checkForMatch(indexOfSingleSelectedCard, position)
            indexOfSingleSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    // check the cards match
    private fun checkForMatch(indexOfSingleSelectedCard: Int?, position: Int): Boolean {
        if (cards[indexOfSingleSelectedCard!!].identifier != cards[position].identifier) {
            return false
        }
        cards[indexOfSingleSelectedCard].isMatched = true
        cards[position].isMatched = true
        numsPairsFound++
        return true
    }

    private fun restore() {
        for (card in cards) {
            if(!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return numsPairsFound == boardSize.getPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }
    // 表示现在走了几步
    fun getNumMoves(): Int {
        return numCardFlip / 2
    }
}