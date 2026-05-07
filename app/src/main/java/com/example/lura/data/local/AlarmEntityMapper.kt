package com.example.lura.data.local

import com.example.lura.data.AlarmSchedule
import com.example.lura.data.AlarmWeekday
import com.example.lura.data.SoundCategory
import com.example.lura.data.SoundItem
import org.json.JSONArray
import java.util.UUID

object AlarmEntityMapper {
    fun createEntity(
        category: SoundCategory,
        sound: SoundItem,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>,
        createdAtEpochMillis: Long
    ): AlarmEntity =
        AlarmEntity(
            id = UUID.randomUUID().toString(),
            categoryId = category.id,
            categoryName = category.name,
            soundId = sound.id,
            soundTitle = sound.title,
            soundTags = encodeTags(sound.tags),
            soundDurationMinutes = sound.durationMinutes,
            hour = hour,
            minute = minute,
            weekdays = weekdays.sortedBy { it.sortOrder },
            isEnabled = true,
            createdAtEpochMillis = createdAtEpochMillis
        )

    fun toDomain(entity: AlarmEntity): AlarmSchedule =
        AlarmSchedule(
            id = entity.id,
            categoryId = entity.categoryId,
            categoryName = entity.categoryName,
            soundId = entity.soundId,
            soundTitle = entity.soundTitle,
            soundTags = decodeTags(entity.soundTags),
            soundDurationMinutes = entity.soundDurationMinutes,
            hour = entity.hour,
            minute = entity.minute,
            weekdays = entity.weekdays.sortedBy { it.sortOrder },
            isEnabled = entity.isEnabled
        )

    fun encodeTags(tags: List<String>): String {
        val jsonArray = JSONArray()
        tags.forEach(jsonArray::put)
        return jsonArray.toString()
    }

    private fun decodeTags(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val jsonArray = JSONArray(value)
        return List(jsonArray.length()) { index -> jsonArray.getString(index) }
    }
}
