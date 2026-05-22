package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val colorArgb: Int = 0xFF1A1A1A.toInt(), // note card custom color
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long = 0L
)
