package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.domain.AutoBackupRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes automatic backups into a folder the user picked with the system folder picker
 * (Storage Access Framework). Everything stays on the device; the app needs no storage or
 * internet permission, only the folder access the user granted.
 */
object AutoBackupWriter {

    /** Keeps access to the picked folder across restarts. Call right after the picker returns. */
    fun takeFolderAccess(context: Context, folderUri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            folderUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        true
    }.getOrDefault(false)

    /** Name of the picked folder for display, or null if it can't be read. */
    fun folderDisplayName(context: Context, folderUri: String?): String? {
        if (folderUri.isNullOrBlank()) return null
        return runCatching {
            val treeUri = Uri.parse(folderUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /**
     * Creates [fileName] in the folder with [content], then removes older automatic backups
     * beyond [AutoBackupRules.KEEP_COUNT]. Only files with the automatic-backup prefix are ever
     * deleted. Returns false if the folder is gone or access was revoked.
     */
    suspend fun write(context: Context, folderUri: String, fileName: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val treeUri = Uri.parse(folderUri)
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                val newDoc = DocumentsContract.createDocument(resolver, parentUri, "application/json", fileName)
                    ?: error("Could not create backup file")
                resolver.openOutputStream(newDoc, "w")?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: error("Could not open backup file")
                pruneOldBackups(context, treeUri, treeDocId)
                true
            }.getOrDefault(false)
        }

    private fun pruneOldBackups(context: Context, treeUri: Uri, treeDocId: String) {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val idsByName = HashMap<String, String>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                idsByName[name] = id
            }
        }
        for (name in AutoBackupRules.filesToDelete(idsByName.keys.toList())) {
            val id = idsByName[name] ?: continue
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
            }
        }
    }
}
