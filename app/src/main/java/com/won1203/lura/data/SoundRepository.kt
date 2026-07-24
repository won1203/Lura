package com.won1203.lura.data

interface SoundRepository {
    suspend fun getCategories(): List<SoundCategory>
    suspend fun getCategory(categoryId: String): SoundCategory?
    suspend fun getRecommendedSound(categoryId: String): SoundItem?
    suspend fun getPlaybackSource(soundId: String, objectKey: String? = null): SoundPlaybackSource
}
