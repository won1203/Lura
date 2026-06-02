package com.lura.core.catalog;

import java.util.List;

public record SoundCatalogItem(
        String id,
        String categoryId,
        String categoryName,
        String categoryDescription,
        String categoryMood,
        String title,
        List<String> tags,
        int durationMinutes,
        String s3Prefix
) {
    public SoundCatalogItem {
        tags = List.copyOf(tags);
        s3Prefix = normalizePrefix(s3Prefix);
    }

    private static String normalizePrefix(String prefix) {
        if (prefix.endsWith("/")) {
            return prefix;
        }
        return prefix + "/";
    }
}
