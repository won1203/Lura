package com.lura.core.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LuraSoundCatalog {

    private static final List<SoundCatalogItem> SOUNDS = List.of(
            new SoundCatalogItem(
                    "random-rain",
                    "rain",
                    "빗소리",
                    "창가에 잔잔히 떨어지는 밤비",
                    "차분함",
                    "랜덤 빗소리",
                    List.of("수면", "비", "차분함"),
                    60,
                    "sounds/rain/"
            ),
            new SoundCatalogItem(
                    "random-water",
                    "water",
                    "물소리",
                    "흐르는 물과 잔잔한 자연의 소리",
                    "안정감",
                    "랜덤 물소리",
                    List.of("수면", "물", "안정감"),
                    60,
                    "sounds/water/"
            ),
            new SoundCatalogItem(
                    "random-wind",
                    "wind",
                    "바람소리",
                    "부드럽게 스치는 바람 소리",
                    "이완",
                    "랜덤 바람소리",
                    List.of("수면", "바람", "이완"),
                    60,
                    "sounds/wind/"
            ),
            new SoundCatalogItem(
                    "random-bird-sound",
                    "bird_sound",
                    "새소리",
                    "아침 숲에서 들리는 잔잔한 새소리",
                    "상쾌함",
                    "랜덤 새소리",
                    List.of("수면", "새소리", "자연"),
                    60,
                    "sounds/bird_sound/"
            ),
            new SoundCatalogItem(
                    "random-firewood",
                    "firewood",
                    "장작소리",
                    "따뜻하게 타오르는 장작의 잔잔한 소리",
                    "포근함",
                    "랜덤 장작소리",
                    List.of("수면", "장작", "포근함"),
                    60,
                    "sounds/firewood/"
            )
    );

    private LuraSoundCatalog() {
    }

    public static List<CategoryCatalogItem> categories() {
        Map<String, CategoryCatalogItem> categoriesById = new LinkedHashMap<>();
        for (SoundCatalogItem sound : SOUNDS) {
            categoriesById.putIfAbsent(
                    sound.categoryId(),
                    new CategoryCatalogItem(
                            sound.categoryId(),
                            sound.categoryName(),
                            sound.categoryDescription(),
                            sound.categoryMood()
                    )
            );
        }
        return List.copyOf(categoriesById.values());
    }

    public static List<SoundCatalogItem> sounds() {
        return SOUNDS;
    }

    public static List<SoundCatalogItem> soundsByCategory(String categoryId) {
        return SOUNDS.stream()
                .filter(sound -> sound.categoryId().equals(categoryId))
                .toList();
    }

    public static Optional<CategoryCatalogItem> findCategory(String categoryId) {
        return categories().stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst();
    }

    public static Optional<SoundCatalogItem> findSound(String soundId) {
        return SOUNDS.stream()
                .filter(sound -> sound.id().equals(soundId))
                .findFirst();
    }
}
