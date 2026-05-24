package com.example.lura.data

import com.example.lura.playback.SleepSoundPlaybackCatalog

object MockSoundRepository : SoundRepository {
    private val categories = listOf(
        SoundCategory(
            id = "rain",
            name = "빗소리",
            description = "창가에 잔잔히 떨어지는 밤비",
            mood = "차분함"
        ),
        SoundCategory(
            id = "water",
            name = "물소리",
            description = "흐르는 물과 잔잔한 자연의 소리",
            mood = "안정감"
        ),
        SoundCategory(
            id = "wind",
            name = "바람소리",
            description = "부드럽게 스치는 바람 소리",
            mood = "이완"
        ),
        SoundCategory(
            id = "bird_sound",
            name = "새소리",
            description = "아침 숲에서 들리는 잔잔한 새소리",
            mood = "상쾌함"
        ),
        SoundCategory(
            id = "firewood",
            name = "장작소리",
            description = "따뜻하게 타오르는 장작의 잔잔한 소리",
            mood = "포근함"
        )
    )

    private val recommendedSounds = listOf(
        SoundItem(
            id = "random-rain",
            categoryId = "rain",
            title = "랜덤 빗소리",
            tags = listOf("수면", "비", "잔잔함"),
            durationMinutes = 60,
            objectKey = "sounds/rain/mock-rain.mp3"
        ),
        SoundItem(
            id = "random-water",
            categoryId = "water",
            title = "랜덤 물소리",
            tags = listOf("수면", "물", "안정감"),
            durationMinutes = 60,
            objectKey = "sounds/water/mock-water.mp3"
        ),
        SoundItem(
            id = "random-wind",
            categoryId = "wind",
            title = "랜덤 바람소리",
            tags = listOf("수면", "바람", "이완"),
            durationMinutes = 60,
            objectKey = "sounds/wind/mock-wind.mp3"
        ),
        SoundItem(
            id = "random-bird-sound",
            categoryId = "bird_sound",
            title = "랜덤 새소리",
            tags = listOf("수면", "새소리", "자연"),
            durationMinutes = 60,
            objectKey = "sounds/bird_sound/mock-bird-sound.mp3"
        ),
        SoundItem(
            id = "random-firewood",
            categoryId = "firewood",
            title = "랜덤 장작소리",
            tags = listOf("수면", "장작", "포근함"),
            durationMinutes = 60,
            objectKey = "sounds/firewood/mock-firewood.mp3"
        )
    )

    override suspend fun getCategories(): List<SoundCategory> = categories

    override suspend fun getCategory(categoryId: String): SoundCategory? =
        categories.firstOrNull { it.id == categoryId }

    override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
        recommendedSounds.firstOrNull { it.categoryId == categoryId }

    override suspend fun getPlaybackSource(soundId: String, objectKey: String?): SoundPlaybackSource {
        val recommendedSound = recommendedSounds.firstOrNull { it.id == soundId }

        return SoundPlaybackSource(
            soundId = soundId,
            categoryId = recommendedSound?.categoryId.orEmpty(),
            objectKey = objectKey ?: recommendedSound?.objectKey ?: "mock/$soundId.mp3",
            sourceUri = SleepSoundPlaybackCatalog.sourceUriFor(soundId)
        )
    }
}
