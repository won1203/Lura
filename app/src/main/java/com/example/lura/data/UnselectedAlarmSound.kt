package com.example.lura.data

object UnselectedAlarmSound {
    const val CATEGORY_ID = "unselected"
    const val SOUND_ID = "unselected"
    const val CATEGORY_NAME = "카테고리 미선택"
    const val SOUND_TITLE = "수면 소리 미선택"

    val category = SoundCategory(
        id = CATEGORY_ID,
        name = CATEGORY_NAME,
        description = "",
        mood = ""
    )

    val sound = SoundItem(
        id = SOUND_ID,
        categoryId = CATEGORY_ID,
        title = SOUND_TITLE,
        tags = emptyList(),
        durationMinutes = 0
    )
}
