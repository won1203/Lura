package com.example.lura.data.remote

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackendSoundRepositoryTest {

    @Test
    fun getPlaybackSourceUsesNextApiCandidateWhenEarlierCandidateFails() = runBlocking {
        val repository = BackendSoundRepository(
            listOf(
                ThrowingLuraBackendApi(IOException("primary unavailable")),
                SuccessfulLuraBackendApi()
            ),
            warningLogger = noOpWarningLogger
        )

        val source = repository.getPlaybackSource("random-firewood")

        assertEquals("random-firewood", source.soundId)
        assertEquals("sounds/firewood/firewood-01.mp3", source.objectKey)
        assertEquals("https://cdn.example.com/firewood-01.mp3", source.sourceUri)
    }

    @Test
    fun getPlaybackSourceKeepsFirstFailureAsCauseWhenAllCandidatesFail() {
        val firstError = IOException("primary timed out")
        val secondError = IOException("emulator host unavailable")
        val repository = BackendSoundRepository(
            listOf(
                ThrowingLuraBackendApi(firstError),
                ThrowingLuraBackendApi(secondError)
            ),
            warningLogger = noOpWarningLogger
        )

        val thrown = org.junit.Assert.assertThrows(IOException::class.java) {
            runBlocking {
                repository.getPlaybackSource("random-firewood")
            }
        }

        assertSame(firstError, thrown)
        assertEquals(listOf(secondError), thrown.suppressed.toList())
    }

    private class SuccessfulLuraBackendApi : LuraBackendApi {
        override suspend fun getCategories(): List<CategoryResponseDto> =
            emptyList()

        override suspend fun getSounds(): List<SoundResponseDto> =
            emptyList()

        override suspend fun getSoundsByCategory(categoryId: String): List<SoundResponseDto> =
            emptyList()

        override suspend fun getSoundPlayUrl(
            soundId: String,
            objectKey: String?
        ): SoundPlayResponseDto =
            SoundPlayResponseDto(
                soundId = soundId,
                categoryId = "firewood",
                objectKey = "sounds/firewood/firewood-01.mp3",
                playUrl = "https://cdn.example.com/firewood-01.mp3"
            )
    }

    private class ThrowingLuraBackendApi(
        private val error: Throwable
    ) : LuraBackendApi {
        override suspend fun getCategories(): List<CategoryResponseDto> =
            throw error

        override suspend fun getSounds(): List<SoundResponseDto> =
            throw error

        override suspend fun getSoundsByCategory(categoryId: String): List<SoundResponseDto> =
            throw error

        override suspend fun getSoundPlayUrl(
            soundId: String,
            objectKey: String?
        ): SoundPlayResponseDto =
            throw error
    }

    private companion object {
        val noOpWarningLogger: (String, Throwable) -> Unit = { _, _ -> }
    }
}
