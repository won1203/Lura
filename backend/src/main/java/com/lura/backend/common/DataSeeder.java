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

            SoundCategory wave = findOrCreateCategory(
                    categoryRepository,
                    "wave",
                    "파도",
                    "느린 호흡을 돕는 해변의 물결",
                    "안정감"
            );

            SoundCategory forest = findOrCreateCategory(
                    categoryRepository,
                    "forest",
                    "숲",
                    "깊은 숲의 바람과 잎사귀 소리",
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
                    "장작 소리",
                    "따뜻하게 타오르는 벽난로의 잔잔한 소리",
                    "포근함"
            );

            createSoundIfMissing(
                    soundRepository,
                    "rain-window-night",
                    rain,
                    "창문 너머 밤비",
                    List.of("수면", "잔잔함", "비"),
                    45,
                    "https://example.com/mock/rain-window-night.mp3"
            );

            createSoundIfMissing(
                    soundRepository,
                    "slow-coast-wave",
                    wave,
                    "느린 해안 파도",
                    List.of("호흡", "파도", "휴식"),
                    60,
                    "https://example.com/mock/slow-coast-wave.mp3"
            );

            createSoundIfMissing(
                    soundRepository,
                    "deep-forest-wind",
                    forest,
                    "깊은 숲의 바람",
                    List.of("숲", "바람", "이완"),
                    50,
                    "https://example.com/mock/deep-forest-wind.mp3"
            );

            createSoundIfMissing(
                    soundRepository,
                    "soft-white-noise",
                    whiteNoise,
                    "부드러운 백색소음",
                    List.of("마스킹", "집중", "수면"),
                    90,
                    "https://example.com/mock/soft-white-noise.mp3"
            );

            createSoundIfMissing(
                    soundRepository,
                    "warm-firewood-night",
                    firewood,
                    "따뜻한 장작불",
                    List.of("장작", "벽난로", "포근함"),
                    70,
                    "https://example.com/mock/warm-firewood-night.mp3"
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

    private void createSoundIfMissing(
            SoundRepository soundRepository,
            String id,
            SoundCategory category,
            String title,
            List<String> tags,
            int durationMinutes,
            String playUrl
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
                playUrl
        ));
    }
}
