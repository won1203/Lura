package com.example.lura.data.remote

import com.example.lura.data.SoundCategory
import com.example.lura.data.SoundItem

data class CategoryResponseDto(
    val id: String,
    val name: String,
    val description: String,
    val mood: String
) {
    fun toDomain(): SoundCategory =
        SoundCategory(
            id = id,
            name = name,
            description = description,
            mood = mood
        )
}

data class SoundResponseDto(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val title: String,
    val tags: List<String>,
    val durationMinutes: Int
) {
    fun toDomain(): SoundItem =
        SoundItem(
            id = id,
            categoryId = categoryId,
            title = title,
            tags = tags,
            durationMinutes = durationMinutes
        )
}

data class SoundPlayResponseDto(
    val soundId: String,
    val playUrl: String
)
