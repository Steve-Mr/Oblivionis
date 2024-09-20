package com.example.ydaynomore.data

import android.net.Uri
import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(data: Date?): Long? {
        return data?.time
    }

    @TypeConverter
    fun fromString(value: String?): Uri? {
        return Uri.parse(value)
    }

    @TypeConverter
    fun uriToString(data: Uri?): String {
        return data.toString()
    }
}