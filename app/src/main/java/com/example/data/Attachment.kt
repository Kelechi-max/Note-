package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val noteId: Int, // Associated note ID (0 if orphaned / independent)
    val name: String,
    val uriString: String, // internal file path / persistent content URI
    val mimeType: String, // image/jpeg, application/pdf, etc.
    val size: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
