package com.example.mymemory.models

import com.google.firebase.firestore.PropertyName

data class UserImageList (
    // firebase will know the key is images, and then map to images list
    @PropertyName("images") val images: List<String>? = null

)
