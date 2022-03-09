package com.example.mymemory

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemory.models.BoardSize
import BitmapScaler
import android.app.AlertDialog
import android.view.View
import android.widget.ProgressBar
import com.example.mymemory.utils.EXTRA_BOARD_SIZE
import com.example.mymemory.utils.EXTRA_GAME_NAME
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CreateActivity"
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14
    }
    private lateinit var boardSize: BoardSize
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var pbUploading: ProgressBar
    private lateinit var startForResult: ActivityResultLauncher<Intent>
    private lateinit var adapter: ImagePickerAdapter

    private var numImagesRequired = -1
    private val storage = Firebase.storage
    private val db = Firebase.firestore
    // uri 说的是指向某个image的directory path
    private val chosenImageUris = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)
        // 从新的activity get了数据后，应该干什么
        startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                Log.w(TAG, "Did not get back from launched activity, user likely canceled flow")
            }
            // clip data 说明user upload 多个images， data 说明user upload 单个image
            val selectUri = result.data?.data
            val clipData = result.data?.clipData
            // 如果图片不为null的话，检查当前需要的图片数量是否满了，未满就把user upload的图片添加进去
            if (clipData != null) {
                Log.i(TAG, "ClipData numImages ${clipData.itemCount}: $clipData")
                for (i in 0 until clipData.itemCount) {
                    val clipItem = clipData.getItemAt(i)
                    if (chosenImageUris.size < numImagesRequired) {
                        chosenImageUris.add(clipItem.uri)
                    }
                }
            } else if (selectUri != null) {
                Log.i(TAG, "data: $selectUri")
                chosenImageUris.add(selectUri)
            }
            adapter.notifyDataSetChanged()
            supportActionBar?.title = "Choose pics (${chosenImageUris.size} / $numImagesRequired)"
            btnSave.isEnabled = shouldEnableSaveButton()
        }

        // 如果user 点击了back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // 拿到传进来的值
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getPairs()
        supportActionBar?.title = "Choose pics(0 / ${numImagesRequired})"

        btnSave.setOnClickListener{
            saveDataToFirebase()
        }
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        // adding text change listener to save button
        etGameName.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

        })
        adapter = ImagePickerAdapter(this, chosenImageUris, boardSize, object: ImagePickerAdapter.ImageClickListener {
            override fun onPlaceholderClicked() {
                launchIntentForPhotos()
            }
        })

        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())

    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 如果user 点击了back button
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shouldEnableSaveButton(): Boolean {
        if (chosenImageUris.size != numImagesRequired) {
            return false
        }
        if (etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true
    }

    private fun saveDataToFirebase() {
        btnSave.isEnabled = false
        val customGameName = etGameName.text.toString()
        Log.i(TAG, "save data to firebase")
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if (document != null && document.data != null) {
                AlertDialog.Builder(this)
                    .setTitle("Name taken")
                    .setMessage("A game already exits with name $customGameName. Please choose another name ")
                    .setPositiveButton("OK", null)
                    .show()
            }else {
                handleImagesUploading(customGameName)
            }
        }.addOnFailureListener{exception ->
            Log.e(TAG, "Encounter error while saving memory game", exception)
            Toast.makeText(this,"Encounter error while saving memory game", Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
        }
        // 看有没有一个图片上传不成功

    }

    private fun handleImagesUploading(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()

        for ((index, photoUri) in chosenImageUris.withIndex()) {
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            // 创造path
            val photoReference = storage.reference.child(filePath)
            // 上传图片
            photoReference.putBytes(imageByteArray).continueWithTask {
                    photoUploadTask -> Log.i(TAG, "Upload bytes: ${photoUploadTask.result?.bytesTransferred}")
                photoReference.downloadUrl
            }.addOnCompleteListener{ downloadUrlTask ->
                if (!downloadUrlTask.isSuccessful) {
                    Log.e(TAG, "Exception with firebase storage")
                    Toast.makeText(this, "Fail to upload image", Toast.LENGTH_SHORT).show()
                    didEncounterError = true
                    return@addOnCompleteListener
                }
                // 如果其中一个图片上传不成，就return
                if (didEncounterError) {
                    pbUploading.visibility = View.GONE
                    return@addOnCompleteListener
                }
                // 成功 放进list
                val downloadUrl = downloadUrlTask.result.toString()
                uploadedImageUrls.add(downloadUrl)
                pbUploading.progress = uploadedImageUrls.size * 100 / chosenImageUris.size
                Log.i(TAG, "Finish uploading $photoUri, num uploaded ${uploadedImageUrls.size} ")
                if (uploadedImageUrls.size == chosenImageUris.size) {
                    handleAllImagesUploaded(gameName, uploadedImageUrls)
                }
            }

        }
    }

    private fun handleAllImagesUploaded(gameName: String, imagesUrl: MutableList<String>) {
        // upload this info to Firestore
        // get path
        db.collection("games").document(gameName)
            .set(mapOf("images" to imagesUrl)) // 把map 存进去，images就是key，imagesUrl是value
            .addOnCompleteListener{ gameCreationTask -> // 如果完成了话
                pbUploading.visibility = View.GONE
                if (!gameCreationTask.isSuccessful) { // 未完成
                    Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                    Toast.makeText(this, "Fail game creation", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                Log.i(TAG, "Successfully creation game $gameName")
                AlertDialog.Builder(this) // 回到main activity
                    .setTitle("Upload complete! Let's play your game '$gameName'")
                    .setPositiveButton("OK") {_, _ -> // 完成所有操作后
                        val resultData = Intent() // 创建intent 返回给main activity
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }
            }
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        // 根据安卓手机系统版本
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        Log.i(TAG, "Scaled width ${scaledBitmap.width} and height ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    // user 放图片
    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        // 只能放image 的intent
        intent.type = "image/*"
        // 允许user input multiple images
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startForResult.launch(Intent.createChooser(intent, "choose pics"))
    }
}