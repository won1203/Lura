package com.example.lura.data.remote

import com.example.lura.data.SoundCategory
import com.example.lura.data.SoundItem
import com.example.lura.data.SoundPlaybackSource
import com.example.lura.data.SoundRepository
import com.example.lura.playback.SleepSoundPlaybackCatalog

class BackendSoundRepository(
    private val api: LuraBackendApi
) : SoundRepository {

    override suspend fun getCategories(): List<SoundCategory> =
        api.getCategories().map(CategoryResponseDto::toDomain)

    override suspend fun getCategory(categoryId: String): SoundCategory? =
        getCategories().firstOrNull { it.id == categoryId }

    override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
        api.getSoundsByCategory(categoryId)
            .firstOrNull()
            ?.toDomain()

    override suspend fun getPlaybackSource(soundId: String, objectKey: String?): SoundPlaybackSource {
        val response = api.getSoundPlayUrl(soundId, objectKey)
        val playUrl = response.playUrl
        val sourceUri = if (playUrl.isBlank() || playUrl.startsWith(MOCK_BACKEND_URL_PREFIX)) {
            SleepSoundPlaybackCatalog.sourceUriFor(soundId)
        } else {
            playUrl
        }

        return SoundPlaybackSource(
            soundId = response.soundId,
            categoryId = response.categoryId,
            objectKey = response.objectKey,
            sourceUri = sourceUri
        )
    }

    private companion object {
        const val MOCK_BACKEND_URL_PREFIX = "https://example.com/mock/"
    }
}
