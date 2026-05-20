package com.lura.backend.common;

import com.lura.backend.category.CategoryRepository;
import com.lura.backend.category.SoundCategory;
import com.lura.backend.sound.Sound;
import com.lura.backend.sound.SoundRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seedData(
            CategoryRepository categoryRepository,
            SoundRepository soundRepository
    ) {
        return args -> {
            SoundCategory rain = findOrCreateCategory(
                    categoryRepository,
                    "rain",
                    "빗소리",
                    "창가에 잔잔히 떨어지는 밤비",
                    "차분함"
            );

            SoundCategory water = findOrCreateCategory(
                    categoryRepository,
                    "water",
                    "물소리",
                    "흐르는 물과 잔잔한 자연의 소리",
                    "안정감"
            );

            SoundCategory wind = findOrCreateCategory(
                    categoryRepository,
                    "wind",
                    "바람소리",
                    "부드럽게 스치는 바람 소리",
                    "이완"
            );

            SoundCategory whiteNoise = findOrCreateCategory(
                    categoryRepository,
                    "white_noise",
                    "백색소음",
                    "외부 소음을 덮어주는 균일한 사운드",
                    "집중"
            );

            SoundCategory firewood = findOrCreateCategory(
                    categoryRepository,
                    "firewood",
                    "장작소리",
                    "따뜻하게 타오르는 장작의 잔잔한 소리",
                    "포근함"
            );

            createRandomSoundSlotIfMissing(
                    soundRepository,
                    "random-rain",
                    rain,
                    "랜덤 빗소리",
                    List.of("수면", "비", "잔잔함"),
                    60,
                    "sounds/rain/"
            );

            createRandomSoundSlotIfMissing(
                    soundRepository,
                    "random-water",
                    water,
                    "랜덤 물소리",
                    List.of("수면", "물", "안정감"),
                    60,
                    "sounds/water/"
            );

            createRandomSoundSlotIfMissing(
                    soundRepository,
                    "random-wind",
                    wind,
                    "랜덤 바람소리",
                    List.of("수면", "바람", "이완"),
                    60,
                    "sounds/wind/"
            );

            createRandomSoundSlotIfMissing(
                    soundRepository,
                    "random-white-noise",
                    whiteNoise,
                    "랜덤 백색소음",
                    List.of("수면", "마스킹", "집중"),
                    60,
                    "sounds/white_noise/"
            );

            createRandomSoundSlotIfMissing(
                    soundRepository,
                    "random-firewood",
                    firewood,
                    "랜덤 장작소리",
                    List.of("수면", "장작", "포근함"),
                    60,
                    "sounds/firewood/"
            );
        };
    }

    private SoundCategory findOrCreateCategory(
            CategoryRepository categoryRepository,
            String id,
            String name,
            String description,
            String mood
    ) {
        return categoryRepository.findById(id)
                .orElseGet(() -> categoryRepository.save(
                        new SoundCategory(id, name, description, mood)
                ));
    }

    private void createRandomSoundSlotIfMissing(
            SoundRepository soundRepository,
            String id,
            SoundCategory category,
            String title,
            List<String> tags,
            int durationMinutes,
            String s3Prefix
    ) {
        if (soundRepository.existsById(id)) {
            return;
        }

        soundRepository.save(new Sound(
                id,
                category,
                title,
                tags,
                durationMinutes,
                s3Prefix
        ));
    }
}
