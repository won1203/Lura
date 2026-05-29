package com.example.lura.data.remote

import android.util.Log
import com.example.lura.data.SoundCategory
import com.example.lura.data.SoundItem
import com.example.lura.data.SoundPlaybackSource
import com.example.lura.data.SoundRepository

private const val BACKEND_SOUND_REPOSITORY_TAG = "BackendSoundRepository"

private fun logBackendWarning(message: String, error: Throwable) {
    Log.w(BACKEND_SOUND_REPOSITORY_TAG, message, error)
}

class BackendSoundRepository(
    private val apis: List<LuraBackendApi>,
    private val warningLogger: (String, Throwable) -> Unit = ::logBackendWarning
) : SoundRepository {

    override suspend fun getCategories(): List<SoundCategory> =
        callApi { api ->
            api.getCategories().map(CategoryResponseDto::toDomain)
        }

    override suspend fun getCategory(categoryId: String): SoundCategory? =
        getCategories().firstOrNull { it.id == categoryId }

    override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
        callApi { api ->
            api.getSoundsByCategory(categoryId)
                .firstOrNull()
                ?.toDomain()
        }

    override suspend fun getPlaybackSource(soundId: String, objectKey: String?): SoundPlaybackSource {
        val response = callApi { api ->
            api.getSoundPlayUrl(soundId, objectKey)
        }
        val playUrl = response.playUrl
        if (playUrl.isBlank() || playUrl.startsWith(MOCK_BACKEND_URL_PREFIX)) {
            error("Backend did not return a playable S3 URL for soundId=$soundId.")
        }

        return SoundPlaybackSource(
            soundId = response.soundId,
            categoryId = response.categoryId,
            objectKey = response.objectKey,
            sourceUri = playUrl
        )
    }

    private suspend fun <T> callApi(block: suspend (LuraBackendApi) -> T): T {
        var firstError: Throwable? = null
        for ((index, api) in apis.withIndex()) {
            try {
                return block(api)
            } catch (error: Throwable) {
                val existingError = firstError
                if (existingError == null) {
                    firstError = error
                } else {
                    existingError.addSuppressed(error)
                }
                warningLogger(
                    "Lura backend API candidate ${index + 1}/${apis.size} failed.",
                    error
                )
            }
        }
        throw firstError ?: IllegalStateException("No Lura backend API candidates configured.")
    }

    private companion object {
        const val MOCK_BACKEND_URL_PREFIX = "https://example.com/mock/"
    }
}
