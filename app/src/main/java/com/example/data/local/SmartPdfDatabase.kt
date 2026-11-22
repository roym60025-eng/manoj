package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.PdfDocumentEntity

@Database(
    entities = [PdfDocumentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SmartPdfDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao

    companion object {
        @Volatile
        private var INSTANCE: SmartPdfDatabase? = null

        fun getInstance(context: Context): SmartPdfDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmartPdfDatabase::class.java,
                    "smart_pdf_maker.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
