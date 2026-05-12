package com.lura.backend.sound;

import com.lura.backend.category.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class SoundService {

    private final SoundRepository soundRepository;
    private final CategoryRepository categoryRepository;

    public SoundService(
            SoundRepository soundRepository,
            CategoryRepository categoryRepository
    ) {
        this.soundRepository = soundRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<SoundResponse> getSounds() {
        return soundRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Sound::getTitle))
                .map(SoundResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SoundResponse> getSoundsByCategory(String categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }

        return soundRepository.findByCategoryId(categoryId)
                .stream()
                .sorted(Comparator.comparing(Sound::getTitle))
                .map(SoundResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SoundPlayResponse getPlayUrl(String soundId) {
        Sound sound = soundRepository.findById(soundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found"));

        return new SoundPlayResponse(sound.getId(), sound.getPlayUrl());
    }
}
