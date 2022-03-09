package com.example.mymemory

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemory.models.BoardSize
import com.example.mymemory.models.MemoryCard
import com.squareup.picasso.Picasso
import kotlin.math.min

class MemoryBoardAdapter(
    private val context: Context,
    private val boardSize: BoardSize,
    private val cards: List<MemoryCard>,
    private val cardClickListener: CardClickListener // 接受一个class，用来设置当card 被click后应该干什么
) : RecyclerView.Adapter<MemoryBoardAdapter.ViewHolder>() {


    companion object{
        // Create the margin for the card
        private const val MARGIN_SIZE = 10
        // 用来设置log的第一个参数
        private const val TAG = "MemoryBoardAdapter"
    }

    interface CardClickListener {
        fun onCardClicked(position: Int)
    }

    // xml 变成kotlin class的
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardWidth = parent.width / boardSize.getWidth() - (2 * MARGIN_SIZE)
        val cardHeight = parent.height / boardSize.getHeight() - (2 * MARGIN_SIZE)
        val cardSideLength = min(cardHeight, cardWidth)
        val view = LayoutInflater.from(context).inflate(R.layout.memory_card, parent, false)
        // 拿到card view 的params, 并且转换为父类
        val layoutParams = view.findViewById<CardView>(R.id.cardView).layoutParams as ViewGroup.MarginLayoutParams
        // 设置后实现了动态设置卡片的大小
        layoutParams.width = cardSideLength
        layoutParams.height = cardSideLength
        layoutParams.setMargins(MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE)
        return ViewHolder(view)
    }
    // 绑定作用
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }
    // how many elements in our recycle view
    override fun getItemCount() = boardSize.numCards

    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        // 拿到button
        private val imageButton = itemView.findViewById<ImageButton>(R.id.imageButton)
        fun bind(position: Int) {
            // 把image和button 连接起来
            val memoryCard = cards[position]
            // 用Face_up去判断现在该呈现什么样子的image
            if (memoryCard.isFaceUp) {
                if (memoryCard.imageUrl != null) {
                    Picasso.get().load(memoryCard.imageUrl).placeholder(R.drawable.ic_image).into(imageButton)
                } else {
                    imageButton.setImageResource(memoryCard.identifier)
                }
            } else {
                imageButton.setImageResource(R.drawable.ic_launcher_background)
            }
            // match 后颜色会变灰
            imageButton.alpha = if (memoryCard.isMatched) .4f else 1.0f
            val colorStateList = if (memoryCard.isMatched) ContextCompat.getColorStateList(context, R.color.color_gray) else null
            ViewCompat.setBackgroundTintList(imageButton, colorStateList)

            imageButton.setOnClickListener{
                Log.i(TAG, "Clicked on position $position")
                cardClickListener.onCardClicked(position)
            }
        }
    }
}
