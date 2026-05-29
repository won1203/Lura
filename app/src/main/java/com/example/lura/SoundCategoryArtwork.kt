package com.example.lura

import androidx.annotation.DrawableRes
import com.example.lura.data.DefaultSoundCatalog

object SoundCategoryArtwork {
    @DrawableRes
    fun backgroundFor(categoryId: String): Int =
        when (categoryId) {
            DefaultSoundCatalog.RAIN_CATEGORY_ID -> R.drawable.rain
            DefaultSoundCatalog.WATER_CATEGORY_ID -> R.drawable.water
            DefaultSoundCatalog.WIND_CATEGORY_ID -> R.drawable.wind
            DefaultSoundCatalog.BIRD_SOUND_CATEGORY_ID -> R.drawable.bird
            DefaultSoundCatalog.FIREWOOD_CATEGORY_ID -> R.drawable.fire
            else -> R.drawable.water
        }
}
