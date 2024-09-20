package com.example.ydaynomore.data;

import android.content.Context
import androidx.room.Database;
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [MediaStoreImage::class], version = 1)
@TypeConverters(Converters::class)
abstract class ImageDatabase: RoomDatabase(){
    abstract fun imageDao(): ImageDao

    companion object {
        @Volatile
        private var INSTANCE: ImageDatabase? = null

        fun getDataBase(context: Context): ImageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ImageDatabase::class.java,
                    "images_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
