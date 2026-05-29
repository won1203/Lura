package com.example.lura.data

object DefaultSoundRepository : SoundRepository {
    override suspend fun getCategories(): List<SoundCategory> =
        DefaultSoundCatalog.categories

    override suspend fun getCategory(categoryId: String): SoundCategory? =
        DefaultSoundCatalog.categories.firstOrNull { it.id == categoryId }

    override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
        DefaultSoundCatalog.recommendedSounds.firstOrNull { it.categoryId == categoryId }

    override suspend fun getPlaybackSource(soundId: String, objectKey: String?): SoundPlaybackSource {
        error("Playback requires a backend-provided S3 URL.")
    }
}
