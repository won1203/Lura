package com.example.lura.data

import com.example.lura.R

internal data class LocalSoundTrack(
    val key: String,
    val categoryId: String,
    val resourceId: Int
)

internal object LocalSoundCatalog {
    val tracksByCategory: Map<String, List<LocalSoundTrack>> = mapOf(
        DefaultSoundCatalog.RAIN_CATEGORY_ID to listOf(
            track("rain_01", DefaultSoundCatalog.RAIN_CATEGORY_ID, R.raw.rain_01),
            track("rain_02", DefaultSoundCatalog.RAIN_CATEGORY_ID, R.raw.rain_02),
            track("rain_03", DefaultSoundCatalog.RAIN_CATEGORY_ID, R.raw.rain_03),
            track("rain_04", DefaultSoundCatalog.RAIN_CATEGORY_ID, R.raw.rain_04),
            track("rain_05", DefaultSoundCatalog.RAIN_CATEGORY_ID, R.raw.rain_05)
        ),
        DefaultSoundCatalog.WATER_CATEGORY_ID to listOf(
            track("water_01", DefaultSoundCatalog.WATER_CATEGORY_ID, R.raw.water_01),
            track("water_02", DefaultSoundCatalog.WATER_CATEGORY_ID, R.raw.water_02),
            track("water_03", DefaultSoundCatalog.WATER_CATEGORY_ID, R.raw.water_03),
            track("water_04", DefaultSoundCatalog.WATER_CATEGORY_ID, R.raw.water_04),
            track("water_05", DefaultSoundCatalog.WATER_CATEGORY_ID, R.raw.water_05)
        ),
        DefaultSoundCatalog.WIND_CATEGORY_ID to listOf(
            track("wind_01", DefaultSoundCatalog.WIND_CATEGORY_ID, R.raw.wind_01),
            track("wind_02", DefaultSoundCatalog.WIND_CATEGORY_ID, R.raw.wind_02),
            track("wind_03", DefaultSoundCatalog.WIND_CATEGORY_ID, R.raw.wind_03),
            track("wind_04", DefaultSoundCatalog.WIND_CATEGORY_ID, R.raw.wind_04),
            track("wind_05", DefaultSoundCatalog.WIND_CATEGORY_ID, R.raw.wind_05)
        ),
        DefaultSoundCatalog.BIRD_SOUND_CATEGORY_ID to listOf(
            track("bird_01", DefaultSoundCatalog.BIRD_SOUND_CATEGORY_ID, R.raw.bird_01),
            track("bird_02", DefaultSoundCatalog.BIRD_SOUND_CATEGORY_ID, R.raw.bird_02),
            track("bird_03", DefaultSoundCatalog.BIRD_SOUND_CATEGORY_ID, R.raw.bird_03),
            track("bird_04", DefaultSoundCatalog.BIRD_SOUND_CATEGORY_ID, R.raw.bird_04),
            track("bird_05", DefaultSoundCatalog.BIRD_SOUND_CATEGORY_ID, R.raw.bird_05)
        ),
        DefaultSoundCatalog.FIREWOOD_CATEGORY_ID to listOf(
            track("fire_01", DefaultSoundCatalog.FIREWOOD_CATEGORY_ID, R.raw.fire_01),
            track("fire_02", DefaultSoundCatalog.FIREWOOD_CATEGORY_ID, R.raw.fire_02),
            track("fire_03", DefaultSoundCatalog.FIREWOOD_CATEGORY_ID, R.raw.fire_03),
            track("fire_04", DefaultSoundCatalog.FIREWOOD_CATEGORY_ID, R.raw.fire_04),
            track("fire_05", DefaultSoundCatalog.FIREWOOD_CATEGORY_ID, R.raw.fire_05)
        )
    )

    private val tracksByKey: Map<String, LocalSoundTrack> =
        tracksByCategory.values
            .flatten()
            .associateBy(LocalSoundTrack::key)

    private val categoryIdBySoundId: Map<String, String> =
        DefaultSoundCatalog.recommendedSounds
            .associate { sound -> sound.id to sound.categoryId }

    fun findTrack(key: String): LocalSoundTrack? =
        tracksByKey[key]

    fun findCategoryId(soundId: String): String? =
        categoryIdBySoundId[soundId]

    private fun track(
        key: String,
        categoryId: String,
        resourceId: Int
    ): LocalSoundTrack =
        LocalSoundTrack(
            key = key,
            categoryId = categoryId,
            resourceId = resourceId
        )
}
