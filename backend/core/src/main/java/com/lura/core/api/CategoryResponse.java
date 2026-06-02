package com.lura.core.api;

import com.lura.core.catalog.CategoryCatalogItem;

public record CategoryResponse(
        String id,
        String name,
        String description,
        String mood
) {
    public static CategoryResponse from(CategoryCatalogItem category) {
        return new CategoryResponse(
                category.id(),
                category.name(),
                category.description(),
                category.mood()
        );
    }
}
