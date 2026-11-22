package com.ehviewer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import com.ehviewer.core.database.model.GalleryTranslationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryTranslationDao {
    @Query("SELECT * FROM gallery_translations WHERE gid = :gid")
    suspend fun getTranslation(gid: Long): GalleryTranslationEntity?

    @Query("SELECT * FROM gallery_translations WHERE gid IN (:gids)")
    suspend fun getTranslations(gids: List<Long>): List<GalleryTranslationEntity>

    @Query("SELECT * FROM gallery_translations WHERE gid = :gid")
    fun flowTranslation(gid: Long): Flow<GalleryTranslationEntity?>

    @Query("SELECT * FROM gallery_translations")
    fun flowAllTranslations(): Flow<List<GalleryTranslationEntity>>

    @Query("SELECT COUNT(*) FROM gallery_translations")
    fun flowTranslationCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM gallery_translations")
    suspend fun countTranslations(): Int

    @Query("DELETE FROM gallery_translations")
    suspend fun clearTranslations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: GalleryTranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(translations: List<GalleryTranslationEntity>)
}
