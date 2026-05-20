package com.lura.backend.sound;

public record SoundPlayResponse(
        String soundId,
        String categoryId,
        String objectKey,
        String playUrl
) {
}
