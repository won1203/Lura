package com.example.lura.data

class CatalogBackedSoundRepository(
    private val catalog: SoundRepository,
    private val remote: SoundRepository
) : SoundRepository {
    override suspend fun getCategories(): List<SoundCategory> =
        catalog.getCategories()

    override suspend fun getCategory(categoryId: String): SoundCategory? =
        catalog.getCategory(categoryId)
            ?: runCatching { remote.getCategory(categoryId) }.getOrNull()

    override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
        catalog.getRecommendedSound(categoryId)
            ?: runCatching { remote.getRecommendedSound(categoryId) }.getOrNull()

    override suspend fun getPlaybackSource(soundId: String, objectKey: String?): SoundPlaybackSource {
        val pinnedObjectKey = objectKey?.takeIf { it.isNotBlank() }
        return runCatching {
            remote.getPlaybackSource(soundId, pinnedObjectKey)
        }.recoverCatching { error ->
            if (pinnedObjectKey == null) {
                throw error
            }
            remote.getPlaybackSource(soundId, null)
        }.getOrThrow()
    }
}
