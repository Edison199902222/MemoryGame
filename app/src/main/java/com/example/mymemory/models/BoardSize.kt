package com.example.mymemory.models
// 给board 创建一个 class 这样就能够动态建立board
enum class BoardSize(val numCards: Int) {
    EASY(8),
    MEDIUM(18),
    HARD(24);

    fun getWidth(): Int {
        return when (this) {
            EASY -> 2
            MEDIUM -> 3
            HARD -> 4
        }
    }

    fun getHeight(): Int {
        return numCards / getWidth()
    }

    fun getPairs(): Int {
        return numCards / 2
    }
}