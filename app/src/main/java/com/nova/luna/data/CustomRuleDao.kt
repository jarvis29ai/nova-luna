package com.nova.luna.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CustomRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CustomRuleEntity): Long

    @Query("SELECT * FROM custom_rules WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledRules(): List<CustomRuleEntity>

    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    suspend fun getAllRules(): List<CustomRuleEntity>

    @Query("DELETE FROM custom_rules")
    suspend fun clearAll()
}

