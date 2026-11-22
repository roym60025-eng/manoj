package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PdfDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY updatedAt DESC")
    fun observeAllDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    suspend fun getById(id: Long): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    fun observeById(id: Long): Flow<PdfDocumentEntity?>

    @Query("SELECT DISTINCT folderName FROM pdf_documents ORDER BY folderName ASC")
    fun observeFolders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: PdfDocumentEntity): Long

    @Update
    suspend fun update(document: PdfDocumentEntity)

    @Delete
    suspend fun delete(document: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
