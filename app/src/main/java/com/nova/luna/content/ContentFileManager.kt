package com.nova.luna.content

import android.content.Context
import android.os.Environment
import java.io.File

class ContentFileManager(private val context: Context) {

    fun saveDraftToFile(draft: ContentDraft, fileName: String): File? {
        // Safe check for storage
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return null

        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(dir, fileName)
            file.writeText(draft.content)
            file
        } catch (e: Exception) {
            null
        }
    }

    fun getFileUri(file: File): String {
        return "file://${file.absolutePath}"
    }
}
