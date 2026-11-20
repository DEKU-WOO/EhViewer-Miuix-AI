package com.ehviewer.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gallery_translations")
data class GalleryTranslationEntity(
    @PrimaryKey
    @ColumnInfo(name = "gid")
    val gid: Long,

    @ColumnInfo(name = "original_title")
    val originalTitle: String,

    @ColumnInfo(name = "translated_title")
    val translatedTitle: String,

    @ColumnInfo(name = "update_time")
    val updateTime: Long = System.currentTimeMillis(),
)
