package com.example.lura.data

import com.example.lura.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSoundRepositoryTest {

    @Test
    fun catalogContainsFiveUniqueTracksForEveryCategory() {
        assertEquals(5, LocalSoundCatalog.tracksByCategory.size)
        assertTrue(
            LocalSoundCatalog.tracksByCategory.values
                .all { tracks -> tracks.size == 5 }
        )

        val allTracks = LocalSoundCatalog.tracksByCategory.values.flatten()
        assertEquals(25, allTracks.size)
        assertEquals(25, allTracks.map(LocalSoundTrack::key).distinct().size)
        assertEquals(25, allTracks.map(LocalSoundTrack::resourceId).distinct().size)
    }

    @Test
    fun getPlaybackSourceSelectsBundledTrackForRequestedCategory() = runBlocking {
        val repository = LocalSoundRepository(
            selectTrack = { tracks -> tracks[2] }
        )

        val source = repository.getPlaybackSource(DefaultSoundCatalog.RAIN_SOUND_ID)

        assertEquals(DefaultSoundCatalog.RAIN_SOUND_ID, source.soundId)
        assertEquals(DefaultSoundCatalog.RAIN_CATEGORY_ID, source.categoryId)
        assertEquals("rain_03", source.objectKey)
        assertEquals("android.resource:///${R.raw.rain_03}", source.sourceUri)
    }

    @Test
    fun getPlaybackSourceKeepsPinnedBundledTrack() = runBlocking {
        val repository = LocalSoundRepository(
            selectTrack = { error("Pinned playback must not select another track.") }
        )

        val source = repository.getPlaybackSource(
            soundId = DefaultSoundCatalog.WIND_SOUND_ID,
            objectKey = "wind_04"
        )

        assertEquals(DefaultSoundCatalog.WIND_CATEGORY_ID, source.categoryId)
        assertEquals("wind_04", source.objectKey)
        assertEquals("android.resource:///${R.raw.wind_04}", source.sourceUri)
    }

    @Test
    fun getPlaybackSourceReplacesLegacyOrInvalidPinnedKey() = runBlocking {
        val repository = LocalSoundRepository(
            selectTrack = { tracks -> tracks.last() }
        )

        val source = repository.getPlaybackSource(
            soundId = DefaultSoundCatalog.FIREWOOD_SOUND_ID,
            objectKey = "legacy/s3/firewood.mp3"
        )

        assertEquals("fire_05", source.objectKey)
        assertNotEquals("legacy/s3/firewood.mp3", source.objectKey)
        assertEquals("android.resource:///${R.raw.fire_05}", source.sourceUri)
    }
}
