package com.example.ydaynomore

import android.app.Application
import com.example.ydaynomore.data.ImageDatabase
import com.example.ydaynomore.data.ImageRepository

class YNMApplication: Application() {

    val database by lazy { ImageDatabase.getDataBase(this) }
    val repository by lazy { ImageRepository(database.imageDao()) }

}