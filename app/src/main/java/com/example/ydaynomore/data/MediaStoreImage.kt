package com.example.ydaynomore.data

import android.net.Uri
import java.util.Date

data class MediaStoreImage(
    val id: Long,
    val displayName: String,
    val dateAdded: Date,
    val contentUri: Uri,
    val width: Int,
    val height: Int
)