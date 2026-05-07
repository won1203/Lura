package com.example.lura.data

interface SoundRepository {
    fun getCategories(): List<SoundCategory>
    fun getCategory(categoryId: String): SoundCategory?
    fun getRecommendedSound(categoryId: String): SoundItem?
}
