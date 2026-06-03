package com.nova.luna.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerText: String,
    val actionType: String,
    val targetValue: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

