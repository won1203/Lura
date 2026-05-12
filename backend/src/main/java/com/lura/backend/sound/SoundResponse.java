package com.lura.backend.sound;

import java.util.List;

public record SoundResponse(
        String id,
        String categoryId,
        String categoryName,
        String title,
        List<String> tags,
        int durationMinutes
) {
    public static SoundResponse from(Sound sound) {
        return new SoundResponse(
                sound.getId(),
                sound.getCategory().getId(),
                sound.getCategory().getName(),
                sound.getTitle(),
                sound.getTags(),
                sound.getDurationMinutes()
        );
    }
}
