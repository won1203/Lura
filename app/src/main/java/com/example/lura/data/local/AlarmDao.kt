package com.example.lura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY createdAtEpochMillis DESC")
    fun getAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :alarmId LIMIT 1")
    fun getAlarm(alarmId: String): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAlarm(alarm: AlarmEntity)

    @Query("UPDATE alarms SET isEnabled = :isEnabled WHERE id = :alarmId")
    fun setAlarmEnabled(alarmId: String, isEnabled: Boolean): Int

    @Query(
        """
        UPDATE alarms
        SET categoryId = :categoryId,
            categoryName = :categoryName,
            soundId = :soundId,
            soundTitle = :soundTitle,
            soundTags = :soundTags,
            soundDurationMinutes = :soundDurationMinutes
        WHERE id = :alarmId
        """
    )
    fun updateAlarmSound(
        alarmId: String,
        categoryId: String,
        categoryName: String,
        soundId: String,
        soundTitle: String,
        soundTags: String,
        soundDurationMinutes: Int
    ): Int

    @Query("DELETE FROM alarms WHERE id = :alarmId")
    fun deleteAlarm(alarmId: String): Int
}
