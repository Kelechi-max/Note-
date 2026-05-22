package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class NoteRepository(
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao,
    private val context: Context
) {
    // Flow of active notes and recycle bin notes
    val activeNotes: Flow<List<Note>> = noteDao.getActiveNotes()
    val deletedNotes: Flow<List<Note>> = noteDao.getDeletedNotes()

    // Flow of recycle bin attachments
    val deletedAttachments: Flow<List<Attachment>> = attachmentDao.getDeletedAttachments()

    // Get attachments for a specific active note
    fun getAttachmentsForNote(noteId: Int): Flow<List<Attachment>> =
        attachmentDao.getAttachmentsForNote(noteId)

    // Notes operations
    suspend fun createNote(title: String, content: String, colorArgb: Int): Int = withContext(Dispatchers.IO) {
        val newNote = Note(
            title = title,
            content = content,
            colorArgb = colorArgb
        )
        noteDao.insertNote(newNote).toInt()
    }

    suspend fun updateNote(note: Note) = withContext(Dispatchers.IO) {
        noteDao.updateNote(note)
    }

    suspend fun moveToRecycleBin(note: Note) = withContext(Dispatchers.IO) {
        // Recycle Note
        val updatedNote = note.copy(
            isDeleted = true,
            deletedTimestamp = System.currentTimeMillis()
        )
        noteDao.updateNote(updatedNote)

        // Recycle all associated attachments for this note as well
        val attachments = attachmentDao.getAllAttachmentsForNoteImmediate(note.id)
        attachments.forEach { attachment ->
            attachmentDao.updateAttachment(
                attachment.copy(
                    isDeleted = true,
                    deletedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun restoreNote(note: Note) = withContext(Dispatchers.IO) {
        // Restore Note
        val updatedNote = note.copy(
            isDeleted = false,
            deletedTimestamp = 0L
        )
        noteDao.updateNote(updatedNote)

        // Restore all associated attachments for this note
        val attachments = attachmentDao.getAllAttachmentsForNoteImmediate(note.id)
        attachments.forEach { attachment ->
            attachmentDao.updateAttachment(
                attachment.copy(
                    isDeleted = false,
                    deletedTimestamp = 0L
                )
            )
        }
    }

    suspend fun permanentlyDeleteNote(note: Note) = withContext(Dispatchers.IO) {
        // Permanent delete Note
        noteDao.deleteNote(note)

        // Permanent delete attachments (and delete physical local files too!)
        val attachments = attachmentDao.getAllAttachmentsForNoteImmediate(note.id)
        attachments.forEach { attachment ->
            deletePhysicalFile(attachment.uriString)
            attachmentDao.deleteAttachment(attachment)
        }
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        // Get all deleted items to purge their physical files first
        // Simple sequential query or direct database wipe
        noteDao.emptyRecycleBin()
        attachmentDao.emptyRecycleBinAttachments()
    }

    // Attachments operations
    suspend fun addAttachmentToNote(noteId: Int, uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        val originalName = getFileName(uri)
        val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(uri)
        val localPath = copyUriToInternalStorage(uri, originalName) ?: return@withContext null
        val size = getFileSize(uri)

        val attachment = Attachment(
            noteId = noteId,
            name = originalName,
            uriString = localPath,
            mimeType = mimeType,
            size = size
        )
        val id = attachmentDao.insertAttachment(attachment).toInt()
        attachment.copy(id = id)
    }

    suspend fun moveToRecycleBin(attachment: Attachment) = withContext(Dispatchers.IO) {
        val updated = attachment.copy(
            isDeleted = true,
            deletedTimestamp = System.currentTimeMillis()
        )
        attachmentDao.updateAttachment(updated)
    }

    suspend fun restoreAttachment(attachment: Attachment) = withContext(Dispatchers.IO) {
        val updated = attachment.copy(
            isDeleted = false,
            deletedTimestamp = 0L
        )
        attachmentDao.updateAttachment(updated)
    }

    suspend fun permanentlyDeleteAttachment(attachment: Attachment) = withContext(Dispatchers.IO) {
        deletePhysicalFile(attachment.uriString)
        attachmentDao.deleteAttachment(attachment)
    }

    // File manipulation helper functions
    private fun copyUriToInternalStorage(uri: Uri, fileName: String): String? {
        return try {
            val destinationFolder = File(context.filesDir, "attachments")
            if (!destinationFolder.exists()) {
                destinationFolder.mkdirs()
            }

            // Generate a unique file name to avoid overwrite conflict
            val extension = fileName.substringAfterLast('.', "")
            val nameWithoutExtension = fileName.substringBeforeLast('.')
            val uniqueName = "${nameWithoutExtension}_${System.currentTimeMillis()}.${extension}"
            val targetFile = File(destinationFolder, uniqueName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun deletePhysicalFile(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Resolving name of files from content URIs
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "attachment_file"
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index != -1) {
                        size = cursor.getLong(index)
                    }
                }
            }
        }
        if (size == 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    size = fd.length
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return size
    }

    private fun getMimeTypeFromExtension(uri: Uri): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}
