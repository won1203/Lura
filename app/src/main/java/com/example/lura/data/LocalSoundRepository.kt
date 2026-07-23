package com.example.lura.data

internal class LocalSoundRepository(
    private val selectTrack: (List<LocalSoundTrack>) -> LocalSoundTrack = { tracks ->
        tracks.random()
    }
) : SoundRepository {

    override suspend fun getCategories(): List<SoundCategory> =
        DefaultSoundCatalog.categories

    override suspend fun getCategory(categoryId: String): SoundCategory? =
        DefaultSoundCatalog.categories.firstOrNull { category -> category.id == categoryId }

    override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
        DefaultSoundCatalog.recommendedSounds
            .firstOrNull { sound -> sound.categoryId == categoryId }

    override suspend fun getPlaybackSource(
        soundId: String,
        objectKey: String?
    ): SoundPlaybackSource {
        val pinnedTrack = objectKey
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(LocalSoundCatalog::findTrack)
        val categoryId = LocalSoundCatalog.findCategoryId(soundId)
            ?: pinnedTrack?.categoryId
            ?: error("Unknown local soundId=$soundId.")
        val categoryTracks = LocalSoundCatalog.tracksByCategory[categoryId]
            .orEmpty()

        check(categoryTracks.isNotEmpty()) {
            "No bundled audio is registered for categoryId=$categoryId."
        }

        val selectedTrack = pinnedTrack
            ?.takeIf { track -> track.categoryId == categoryId }
            ?: selectTrack(categoryTracks)

        return SoundPlaybackSource(
            soundId = soundId,
            categoryId = categoryId,
            objectKey = selectedTrack.key,
            sourceUri = "$ANDROID_RESOURCE_URI_PREFIX${selectedTrack.resourceId}"
        )
    }

    private companion object {
        const val ANDROID_RESOURCE_URI_PREFIX = "android.resource:///"
    }
}
