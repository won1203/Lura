package com.lura.backend.category;

public record CategoryResponse(
        String id,
        String name,
        String description,
        String mood
) {
    public static CategoryResponse from(SoundCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getMood()
        );
    }
}
