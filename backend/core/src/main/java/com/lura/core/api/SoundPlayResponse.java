package com.lura.core.api;

public record SoundPlayResponse(
        String soundId,
        String categoryId,
        String objectKey,
        String playUrl
) {
}
