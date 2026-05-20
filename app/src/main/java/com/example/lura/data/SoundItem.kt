package com.example.lura.data

data class SoundItem(
    val id: String,
    val categoryId: String,
    val title: String,
    val tags: List<String>,
    val durationMinutes: Int,
    val objectKey: String = ""
)
