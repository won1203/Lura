package com.lura.core.api;

import com.lura.core.catalog.SoundCatalogItem;

import java.util.List;

public record SoundResponse(
        String id,
        String categoryId,
        String categoryName,
        String title,
        List<String> tags,
        int durationMinutes
) {
    public static SoundResponse from(SoundCatalogItem sound) {
        return new SoundResponse(
                sound.id(),
                sound.categoryId(),
                sound.categoryName(),
                sound.title(),
                sound.tags(),
                sound.durationMinutes()
        );
    }
}
