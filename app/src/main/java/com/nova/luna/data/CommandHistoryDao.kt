package com.nova.luna.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommandHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommandHistoryEntity): Long

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CommandHistoryEntity>

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}
