package com.example.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemory.models.BoardSize
import com.example.mymemory.models.MemoryGame
import com.example.mymemory.models.UserImageList
import com.example.mymemory.utils.EXTRA_BOARD_SIZE
import com.example.mymemory.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    private lateinit var clRoot: CoordinatorLayout
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    // 用来替代之前的
    private lateinit var startForResult: ActivityResultLauncher<Intent>
    // 建立board
    private var boardSize: BoardSize = BoardSize.EASY
    private var customGameImages: List<String>? = null
    private var gameName: String? = null
    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                // Handle the Intent
                //do stuff here
                val customGameName = intent?.getStringExtra(EXTRA_GAME_NAME)
                if (customGameName == null) {
                    Log.e(TAG, "Got null custom game from Create activity")
                } else {
                    downloadGame(customGameName)
                }

            }
        }
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)
        clRoot = findViewById(R.id.clRoot)

        setupBoard()
    }
    // 从firebase 里面下载图片并且把图片放在grid上
    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImagesList = document.toObject(UserImageList::class.java)
            if (userImagesList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(clRoot, "Sorry we couldn't find such a game, $customGameName", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImagesList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImagesList.images
            for (imagesUrl in userImagesList.images) {
                Picasso.get().load(imagesUrl).fetch()
            }
            Snackbar.make(clRoot, "You're now playing '$customGameName'!", Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setupBoard()
        }.addOnFailureListener{exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    // 如果有option 被点击了
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit your current game?", null, View.OnClickListener {
                        //重新开始游戏
                        setupBoard()
                    })
                } else {
                    setupBoard()
                }
            }
            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            // Grab the text of game name that user want to download
            // 用view 去寻找
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        // easy medium hard的按钮
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroupSize)
        showAlertDialog("Create your own memory game", boardSizeView, View.OnClickListener {
            // set new board size
            val desireBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            //
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, BoardSize.EASY)
            startForResult.launch(intent)
        })


    }

    // 选中新size后 应该怎么更新
    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        // easy medium hard的按钮
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroupSize)
        // 把当前board的size check掉，不能被选中
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            else -> radioGroupSize.check(R.id.rbHard)
        }
        // 选中新size
        showAlertDialog("Choose the new size", boardSizeView, View.OnClickListener {
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun updateGameWithFlip(position: Int) {
        // error checking
        // 检查游戏是否结束
        if (memoryGame.haveWonGame()) {
            Snackbar.make(clRoot, "You already won", Snackbar.LENGTH_LONG).show()
            return
        }
        //检查当前卡片是否以及被翻转
        if (memoryGame.isCardFaceUp(position)) {
            Snackbar.make(clRoot, "Invalid move", Snackbar.LENGTH_LONG).show()
            return
        }
        // 如果卡片被match
        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found a match! Num pairs found ${memoryGame.numsPairsFound}")
            tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
            // 用来改变右下角pair颜色的，每当找到pair的时候，颜色改变
            val color = ArgbEvaluator().evaluate(
                memoryGame.numsPairsFound.toFloat() / boardSize.getPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numsPairsFound} / ${boardSize.getPairs()}"
            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, "You already won", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }

    private fun setupBoard() {
        supportActionBar?.title = gameName ?:getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumPairs.text = "Pairs: 0 / 4"
                tvNumMoves.text = "Easy: 4 x 2"
            }
            BoardSize.MEDIUM -> {
                tvNumPairs.text = "Pairs: 0 / 9"
                tvNumMoves.text = "Medium: 6 x 3"
            }
            BoardSize.HARD -> {
                tvNumPairs.text = "Pairs: 0 / 12"
                tvNumMoves.text = "Hard: 6 x 6"
            }
        }
        memoryGame = MemoryGame(boardSize, customGameImages)
        // 作用是把data set 和 RecyclerView的view 连接起来，第一个参数是在哪里显示，第二个是一共有几个
        // 最后一个参数是匿名class，extend了CardClickListener 接口
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener {
            // 重写
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }
        })
        rvBoard.adapter = adapter
        // 提升performance用的
        rvBoard.setHasFixedSize(true)
        // span count is how many columns in our game
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }
    // 用来给提示消息的
    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }
}