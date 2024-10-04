package top.maary.oblivionis

import android.app.Application
import top.maary.oblivionis.data.ImageDatabase
import top.maary.oblivionis.data.ImageRepository

class OblivionisApplication: Application() {

    private val database by lazy { ImageDatabase.getDataBase(this) }
    val repository by lazy { ImageRepository(database.imageDao()) }

}