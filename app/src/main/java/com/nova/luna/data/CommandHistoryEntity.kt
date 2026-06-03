package com.nova.luna.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val normalizedText: String,
    val intentType: String,
    val actionType: String,
    val safetyLevel: String,
    val safetyMessage: String,
    val resultMessage: String,
    val success: Boolean,
    val shouldStopListening: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

