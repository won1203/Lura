package com.example.lura.data

object DefaultSoundCatalog {
    const val RAIN_CATEGORY_ID = "rain"
    const val WATER_CATEGORY_ID = "water"
    const val WIND_CATEGORY_ID = "wind"
    const val BIRD_SOUND_CATEGORY_ID = "bird_sound"
    const val FIREWOOD_CATEGORY_ID = "firewood"
    const val RAIN_SOUND_ID = "random-rain"
    const val WATER_SOUND_ID = "random-water"
    const val WIND_SOUND_ID = "random-wind"
    const val BIRD_SOUND_ID = "random-bird-sound"
    const val FIREWOOD_SOUND_ID = "random-firewood"

    val categories = listOf(
        SoundCategory(
            id = RAIN_CATEGORY_ID,
            name = "빗소리",
            description = "창가에 잔잔히 떨어지는 밤비",
            mood = "차분함"
        ),
        SoundCategory(
            id = WATER_CATEGORY_ID,
            name = "물소리",
            description = "흐르는 물과 잔잔한 자연의 소리",
            mood = "안정감"
        ),
        SoundCategory(
            id = WIND_CATEGORY_ID,
            name = "바람소리",
            description = "부드럽게 스치는 바람 소리",
            mood = "이완"
        ),
        SoundCategory(
            id = BIRD_SOUND_CATEGORY_ID,
            name = "새소리",
            description = "아침 숲에서 들리는 잔잔한 새소리",
            mood = "상쾌함"
        ),
        SoundCategory(
            id = FIREWOOD_CATEGORY_ID,
            name = "장작소리",
            description = "따뜻하게 타오르는 장작의 잔잔한 소리",
            mood = "포근함"
        )
    )

    val recommendedSounds = listOf(
        SoundItem(
            id = RAIN_SOUND_ID,
            categoryId = RAIN_CATEGORY_ID,
            title = "랜덤 빗소리",
            tags = listOf("수면", "비", "잔잔함"),
            durationMinutes = 60
        ),
        SoundItem(
            id = WATER_SOUND_ID,
            categoryId = WATER_CATEGORY_ID,
            title = "랜덤 물소리",
            tags = listOf("수면", "물", "안정감"),
            durationMinutes = 60
        ),
        SoundItem(
            id = WIND_SOUND_ID,
            categoryId = WIND_CATEGORY_ID,
            title = "랜덤 바람소리",
            tags = listOf("수면", "바람", "이완"),
            durationMinutes = 60
        ),
        SoundItem(
            id = BIRD_SOUND_ID,
            categoryId = BIRD_SOUND_CATEGORY_ID,
            title = "랜덤 새소리",
            tags = listOf("수면", "새소리", "자연"),
            durationMinutes = 60
        ),
        SoundItem(
            id = FIREWOOD_SOUND_ID,
            categoryId = FIREWOOD_CATEGORY_ID,
            title = "랜덤 장작소리",
            tags = listOf("수면", "장작", "포근함"),
            durationMinutes = 60
        )
    )
}
