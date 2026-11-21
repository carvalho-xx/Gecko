package io.github.carvalhoxx.gecko.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "CUSTOM_LIST")
data class CustomListEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_franchise")
    val is_franchise: Boolean = false,

    @ColumnInfo(name = "external_franchise_id")
    val external_franchise_id: Long? = null
)
