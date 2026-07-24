package com.example.lura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lura.data.AlarmWeekday

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

    @Query("UPDATE alarms SET isEnabled = 0 WHERE isEnabled = 1")
    fun disableEnabledAlarms(): Int

    @Query(
        """
        UPDATE alarms
        SET categoryId = :categoryId,
            categoryName = :categoryName,
            soundId = :soundId,
            soundTitle = :soundTitle,
            soundTags = :soundTags,
            soundDurationMinutes = :soundDurationMinutes,
            soundObjectKey = :soundObjectKey
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
        soundDurationMinutes: Int,
        soundObjectKey: String
    ): Int

    @Query(
        """
        UPDATE alarms
        SET sleepStartHour = :sleepStartHour,
            sleepStartMinute = :sleepStartMinute,
            hour = :hour,
            minute = :minute
        WHERE id = :alarmId
        """
    )
    fun updateAlarmTimes(
        alarmId: String,
        sleepStartHour: Int,
        sleepStartMinute: Int,
        hour: Int,
        minute: Int
    ): Int

    @Query("UPDATE alarms SET weekdays = :weekdays WHERE id = :alarmId")
    fun updateAlarmWeekdays(alarmId: String, weekdays: List<AlarmWeekday>): Int

    @Query("UPDATE alarms SET soundObjectKey = :soundObjectKey WHERE id = :alarmId")
    fun updateAlarmSoundObjectKey(alarmId: String, soundObjectKey: String): Int

    @Query("DELETE FROM alarms WHERE id = :alarmId")
    fun deleteAlarm(alarmId: String): Int
}
