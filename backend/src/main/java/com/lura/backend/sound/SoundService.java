package com.lura.backend.sound;

import com.lura.backend.category.CategoryRepository;
import com.lura.backend.storage.S3RandomSoundSelector;
import com.lura.backend.storage.S3PresignedUrlService;
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
    private final S3RandomSoundSelector s3RandomSoundSelector;
    private final S3PresignedUrlService s3PresignedUrlService;

    public SoundService(
            SoundRepository soundRepository,
            CategoryRepository categoryRepository,
            S3RandomSoundSelector s3RandomSoundSelector,
            S3PresignedUrlService s3PresignedUrlService
    ) {
        this.soundRepository = soundRepository;
        this.categoryRepository = categoryRepository;
        this.s3RandomSoundSelector = s3RandomSoundSelector;
        this.s3PresignedUrlService = s3PresignedUrlService;
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
    public SoundPlayResponse getPlayUrl(String soundId, String objectKey) {
        Sound sound = soundRepository.findById(soundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found"));

        String selectedObjectKey = resolvePlaybackObjectKey(sound, objectKey);
        String presignedUrl = s3PresignedUrlService.createGetObjectUrl(selectedObjectKey);

        return new SoundPlayResponse(
                sound.getId(),
                sound.getCategory().getId(),
                selectedObjectKey,
                presignedUrl
        );
    }

    private String resolvePlaybackObjectKey(Sound sound, String objectKey) {
        if (objectKey != null && !objectKey.isBlank()) {
            validateObjectKeyBelongsToSound(sound, objectKey);
            return objectKey;
        }

        return s3RandomSoundSelector.selectRandomAudioObjectKey(sound.getS3Prefix())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No playable S3 object found for category"
                ));
    }

    private void validateObjectKeyBelongsToSound(Sound sound, String objectKey) {
        if (!objectKey.startsWith(sound.getS3Prefix())
                || !s3RandomSoundSelector.isExistingPlayableAudioObjectKey(objectKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid playback object key");
        }
    }

    @Transactional(readOnly = true)
    public SoundPlayResponse getRandomPlayUrlByCategory(String categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }

        Sound sound = soundRepository.findByCategoryId(categoryId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found"));

        return getPlayUrl(sound.getId(), null);
    }
}
