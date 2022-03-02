package com.example.mymemory

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.mymemory.models.BoardSize
import com.example.mymemory.utils.EXTRA_BOARD_SIZE

class CreateActivity : AppCompatActivity() {
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)
        // 如果user 点击了back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // 拿到传进来的值
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getPairs()
        supportActionBar?.title = "Choose pics(0 / ${numImagesRequired})"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 如果user 点击了back button
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}