package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val folderName: String = "Smart PDF Maker",
    val category: String = PdfCategory.CREATED.name,
    val pageCount: Int = 1,
    val sizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val thumbnailPath: String? = null
)
