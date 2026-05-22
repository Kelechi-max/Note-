package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getActiveAttachments(): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedAttachments(): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getAttachmentsForNote(noteId: Int): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId")
    fun getAllAttachmentsForNoteImmediate(noteId: Int): List<Attachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: Attachment): Long

    @Update
    suspend fun updateAttachment(attachment: Attachment)

    @Delete
    suspend fun deleteAttachment(attachment: Attachment)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteAttachmentsByNoteId(noteId: Int)

    @Query("DELETE FROM attachments WHERE isDeleted = 1")
    suspend fun emptyRecycleBinAttachments()
}
