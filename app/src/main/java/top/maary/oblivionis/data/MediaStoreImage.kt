package top.maary.oblivionis.data

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "images")
data class MediaStoreImage(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "date_added") val dateAdded: Date,
    @ColumnInfo(name = "content_uri") val contentUri: Uri,
    @ColumnInfo(name = "is_marked") var isMarked: Boolean = false,
    @ColumnInfo(name = "is_excluded", defaultValue = "0") var isExcluded: Boolean = false
)