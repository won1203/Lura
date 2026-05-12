package com.example.lura.data.remote

import com.example.lura.data.SoundCategory
import com.example.lura.data.SoundItem
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

    override suspend fun getPlaybackSourceUri(soundId: String): String {
        val playUrl = api.getSoundPlayUrl(soundId).playUrl

        return if (playUrl.isBlank() || playUrl.startsWith(MOCK_BACKEND_URL_PREFIX)) {
            // The current backend returns placeholder URLs until S3 is connected, so playback
            // still uses the local test source while validating that the /play API is called.
            SleepSoundPlaybackCatalog.sourceUriFor(soundId)
        } else {
            playUrl
        }
    }

    private companion object {
        const val MOCK_BACKEND_URL_PREFIX = "https://example.com/mock/"
    }
}
