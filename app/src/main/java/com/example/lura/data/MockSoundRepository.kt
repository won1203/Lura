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
            id = "wave",
            name = "파도",
            description = "느린 호흡을 돕는 해변의 물결",
            mood = "안정감"
        ),
        SoundCategory(
            id = "forest",
            name = "숲",
            description = "깊은 숲의 바람과 잎사귀 소리",
            mood = "이완"
        ),
        SoundCategory(
            id = "white_noise",
            name = "백색소음",
            description = "외부 소음을 덮어주는 균일한 사운드",
            mood = "집중"
        ),
        SoundCategory(
            id = "firewood",
            name = "장작 소리",
            description = "따뜻하게 타오르는 벽난로의 잔잔한 소리",
            mood = "포근함"
        )
    )

    private val recommendedSounds = listOf(
        SoundItem(
            id = "rain-window-night",
            categoryId = "rain",
            title = "창문 너머 밤비",
            tags = listOf("수면", "잔잔함", "비"),
            durationMinutes = 45
        ),
        SoundItem(
            id = "slow-coast-wave",
            categoryId = "wave",
            title = "느린 해안 파도",
            tags = listOf("호흡", "파도", "휴식"),
            durationMinutes = 60
        ),
        SoundItem(
            id = "deep-forest-wind",
            categoryId = "forest",
            title = "깊은 숲의 바람",
            tags = listOf("숲", "바람", "이완"),
            durationMinutes = 50
        ),
        SoundItem(
            id = "soft-white-noise",
            categoryId = "white_noise",
            title = "부드러운 백색소음",
            tags = listOf("마스킹", "집중", "수면"),
            durationMinutes = 90
        ),
        SoundItem(
            id = "warm-firewood-night",
            categoryId = "firewood",
            title = "따뜻한 장작불",
            tags = listOf("장작", "벽난로", "포근함"),
            durationMinutes = 70
        )
    )

    override suspend fun getCategories(): List<SoundCategory> = categories

    override suspend fun getCategory(categoryId: String): SoundCategory? =
        categories.firstOrNull { it.id == categoryId }

    override suspend fun getRecommendedSound(categoryId: String): SoundItem? =
        recommendedSounds.firstOrNull { it.categoryId == categoryId }

    override suspend fun getPlaybackSource(soundId: String, objectKey: String?): SoundPlaybackSource =
        SoundPlaybackSource(
            soundId = soundId,
            categoryId = recommendedSounds.firstOrNull { it.id == soundId }?.categoryId.orEmpty(),
            objectKey = objectKey ?: "mock/$soundId.mp3",
            sourceUri = SleepSoundPlaybackCatalog.sourceUriFor(soundId)
        )
}
