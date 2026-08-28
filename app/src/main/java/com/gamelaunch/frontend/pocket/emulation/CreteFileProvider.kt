package com.gamelaunch.frontend.pocket.emulation

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider

/**
 * FileProvider with the read-only metadata columns expected by emulator SAF bridges.
 *
 * AndroidX FileProvider normally exposes only display name and size. NetherSX2 also queries
 * MIME type and last-modified before opening a URI, and rejects the ROM when those columns are
 * absent. File access and path validation still remain entirely delegated to FileProvider.
 */
class CreteFileProvider : FileProvider() {
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        var displayName = uri.lastPathSegment.orEmpty()
        var size = 0L

        super.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            selection,
            selectionArgs,
            sortOrder
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }

        val requestedColumns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        return MatrixCursor(requestedColumns, 1).apply {
            addRow(requestedColumns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> displayName
                    OpenableColumns.SIZE -> size
                    DocumentsContract.Document.COLUMN_MIME_TYPE ->
                        getType(uri) ?: "application/octet-stream"
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 0L
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> uri.toString()
                    else -> null
                }
            })
        }
    }
}
