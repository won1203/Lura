package com.lura.backend.common;

import com.lura.backend.category.CategoryRepository;
import com.lura.backend.category.SoundCategory;
import com.lura.backend.sound.Sound;
import com.lura.backend.sound.SoundRepository;
import com.lura.core.catalog.LuraSoundCatalog;
import com.lura.core.catalog.SoundCatalogItem;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seedData(
            CategoryRepository categoryRepository,
            SoundRepository soundRepository
    ) {
        return args -> {
            for (SoundCatalogItem sound : LuraSoundCatalog.sounds()) {
                SoundCategory category = categoryRepository.save(new SoundCategory(
                        sound.categoryId(),
                        sound.categoryName(),
                        sound.categoryDescription(),
                        sound.categoryMood()
                ));
                upsertRandomSoundSlot(soundRepository, sound, category);
            }
        };
    }

    private void upsertRandomSoundSlot(
            SoundRepository soundRepository,
            SoundCatalogItem sound,
            SoundCategory category
    ) {
        soundRepository.save(new Sound(
                sound.id(),
                category,
                sound.title(),
                sound.tags(),
                sound.durationMinutes(),
                sound.s3Prefix()
        ));
    }
}
