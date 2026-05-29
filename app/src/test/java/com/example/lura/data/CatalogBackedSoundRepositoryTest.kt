package com.example.lura.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBackedSoundRepositoryTest {

    @Test
    fun getCategoriesReturnsBundledCatalogWithoutCallingRemote() = runBlocking {
        val remote = CountingSoundRepository()
        val repository = CatalogBackedSoundRepository(
            catalog = DefaultSoundRepository,
            remote = remote
        )

        val categories = repository.getCategories()

        assertEquals(
            DefaultSoundCatalog.categories.map { it.id },
            categories.map { it.id }
        )
        assertEquals(0, remote.getCategoriesCalls)
    }

    @Test
    fun getRecommendedSoundReturnsBundledSoundWithoutCallingRemoteForKnownCategory() = runBlocking {
        val remote = CountingSoundRepository()
        val repository = CatalogBackedSoundRepository(
            catalog = DefaultSoundRepository,
            remote = remote
        )

        val sound = repository.getRecommendedSound(DefaultSoundCatalog.RAIN_CATEGORY_ID)

        assertEquals(DefaultSoundCatalog.RAIN_SOUND_ID, sound?.id)
        assertEquals(0, remote.getRecommendedSoundCalls)
    }

    @Test
    fun bundledRecommendedSoundsDoNotCarryMockObjectKeys() {
        assertTrue(DefaultSoundCatalog.recommendedSounds.all { it.objectKey.isBlank() })
    }

    @Test
    fun getPlaybackSourceUsesRemoteWhenAvailable() = runBlocking {
        val repository = CatalogBackedSoundRepository(
            catalog = DefaultSoundRepository,
            remote = RemotePlaybackRepository()
        )

        val source = repository.getPlaybackSource(DefaultSoundCatalog.RAIN_SOUND_ID)

        assertEquals("https://cdn.example.com/rain.mp3", source.sourceUri)
        assertEquals("sounds/rain/remote-rain.mp3", source.objectKey)
    }

    @Test
    fun getPlaybackSourceRetriesWithoutPinnedObjectKeyWhenPinnedKeyFails() = runBlocking {
        val remote = RejectingPinnedObjectKeyRepository()
        val repository = CatalogBackedSoundRepository(
            catalog = DefaultSoundRepository,
            remote = remote
        )

        val source = repository.getPlaybackSource(
            soundId = DefaultSoundCatalog.FIREWOOD_SOUND_ID,
            objectKey = "sounds/firewood/mock-firewood.mp3"
        )

        assertEquals(DefaultSoundCatalog.FIREWOOD_SOUND_ID, source.soundId)
        assertEquals("sounds/firewood/real-firewood.mp3", source.objectKey)
        assertEquals(listOf("sounds/firewood/mock-firewood.mp3", null), remote.requestedObjectKeys)
    }

    @Test
    fun getPlaybackSourceDoesNotFallBackToBundledAudioWhenRemoteFails() {
        val repository = CatalogBackedSoundRepository(
            catalog = DefaultSoundRepository,
            remote = FailingSoundRepository
        )

        assertThrows(IOException::class.java) {
            runBlocking {
                repository.getPlaybackSource(DefaultSoundCatalog.RAIN_SOUND_ID)
            }
        }
    }

    private class CountingSoundRepository : SoundRepository {
        var getCategoriesCalls = 0
        var getRecommendedSoundCalls = 0

        override suspend fun getCategories(): List<SoundCategory> {
            getCategoriesCalls += 1
            return emptyList()
        }

        override suspend fun getCategory(categoryId: String): SoundCategory? =
            null

        override suspend fun getRecommendedSound(categoryId: String): SoundItem? {
            getRecommendedSoundCalls += 1
            return null
        }

        override suspend fun getPlaybackSource(
            soundId: String,
            objectKey: String?
        ): SoundPlaybackSource =
            throw IOException("Remote playback is unavailable.")
    }

    private class RemotePlaybackRepository : SoundRepository {
        override suspend fun getCategories(): List<SoundCategory> =
            emptyList()

        override suspend fun getCategory(categoryId: String): SoundCategory? =
            null

        override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
            null

        override suspend fun getPlaybackSource(
            soundId: String,
            objectKey: String?
        ): SoundPlaybackSource =
            SoundPlaybackSource(
                soundId = soundId,
                categoryId = DefaultSoundCatalog.RAIN_CATEGORY_ID,
                objectKey = "sounds/rain/remote-rain.mp3",
                sourceUri = "https://cdn.example.com/rain.mp3"
            )
    }

    private class RejectingPinnedObjectKeyRepository : SoundRepository {
        val requestedObjectKeys = mutableListOf<String?>()

        override suspend fun getCategories(): List<SoundCategory> =
            emptyList()

        override suspend fun getCategory(categoryId: String): SoundCategory? =
            null

        override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
            null

        override suspend fun getPlaybackSource(
            soundId: String,
            objectKey: String?
        ): SoundPlaybackSource {
            requestedObjectKeys += objectKey
            if (objectKey != null) {
                throw IOException("Pinned object key is invalid.")
            }

            return SoundPlaybackSource(
                soundId = soundId,
                categoryId = DefaultSoundCatalog.FIREWOOD_CATEGORY_ID,
                objectKey = "sounds/firewood/real-firewood.mp3",
                sourceUri = "https://cdn.example.com/firewood.mp3"
            )
        }
    }

    private object FailingSoundRepository : SoundRepository {
        override suspend fun getCategories(): List<SoundCategory> =
            throw IOException("Remote categories are unavailable.")

        override suspend fun getCategory(categoryId: String): SoundCategory? =
            throw IOException("Remote category is unavailable.")

        override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
            throw IOException("Remote sound is unavailable.")

        override suspend fun getPlaybackSource(
            soundId: String,
            objectKey: String?
        ): SoundPlaybackSource =
            throw IOException("Remote playback is unavailable.")
    }
}
