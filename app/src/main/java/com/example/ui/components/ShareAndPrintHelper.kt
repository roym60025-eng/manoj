package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.data.model.PdfDocumentEntity
import com.example.pdf.StorageHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ShareAndPrintHelper {

    fun sharePdf(context: Context, storageHelper: StorageHelper, doc: PdfDocumentEntity) {
        val file = File(doc.filePath)
        if (!file.exists()) return
        val uri = storageHelper.getFileProviderUri(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${doc.name}.pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    fun shareMultiplePdfs(
        context: Context,
        storageHelper: StorageHelper,
        docs: List<PdfDocumentEntity>
    ) {
        if (docs.isEmpty()) return
        if (docs.size == 1) {
            sharePdf(context, storageHelper, docs.first())
            return
        }
        val uris = ArrayList<Uri>(
            docs.mapNotNull { doc ->
                val f = File(doc.filePath)
                if (f.exists()) storageHelper.getFileProviderUri(f) else null
            }
        )
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${uris.size} PDFs"))
    }

    fun shareImages(context: Context, storageHelper: StorageHelper, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.filter { it.exists() }.map { storageHelper.getFileProviderUri(it) })
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share Converted Images"))
    }

    fun printPdf(context: Context, file: File, jobName: String) {
        if (!file.exists()) return
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Missing print destination")
                    return
                }
                runCatching {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } > 0) {
                                if (cancellationSignal?.isCanceled == true) {
                                    callback?.onWriteCancelled()
                                    return
                                }
                                output.write(buf, 0, read)
                            }
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure {
                    callback?.onWriteFailed(it.message)
                }
            }
        }
        printManager.print(jobName, adapter, null)
    }

    fun shareApp(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out Smart PDF Maker — create, scan, edit, compress, merge, and manage PDFs effortlessly!"
            )
        }
        context.startActivity(Intent.createChooser(intent, "Share Smart PDF Maker"))
    }
}
