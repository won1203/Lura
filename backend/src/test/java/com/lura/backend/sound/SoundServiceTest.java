package com.lura.backend.sound;

import com.lura.backend.category.CategoryRepository;
import com.lura.backend.category.SoundCategory;
import com.lura.backend.storage.S3PresignedUrlService;
import com.lura.backend.storage.S3RandomSoundSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoundServiceTest {

    @Mock
    private SoundRepository soundRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private S3RandomSoundSelector s3RandomSoundSelector;

    @Mock
    private S3PresignedUrlService s3PresignedUrlService;

    @InjectMocks
    private SoundService soundService;

    @Test
    void getPlayUrlRejectsMissingPinnedObjectKey() {
        Sound sound = firewoodSound();
        String objectKey = "sounds/firewood/mock-firewood.mp3";

        when(soundRepository.findById("random-firewood")).thenReturn(Optional.of(sound));
        when(s3RandomSoundSelector.isExistingPlayableAudioObjectKey(objectKey)).thenReturn(false);

        assertThrows(
                ResponseStatusException.class,
                () -> soundService.getPlayUrl("random-firewood", objectKey)
        );
        verify(s3PresignedUrlService, never()).createGetObjectUrl(anyString());
    }

    @Test
    void getPlayUrlReturnsPresignedUrlForExistingPinnedObjectKey() {
        Sound sound = firewoodSound();
        String objectKey = "sounds/firewood/real-firewood.mp3";
        String playUrl = "https://s3.example.com/real-firewood.mp3";

        when(soundRepository.findById("random-firewood")).thenReturn(Optional.of(sound));
        when(s3RandomSoundSelector.isExistingPlayableAudioObjectKey(objectKey)).thenReturn(true);
        when(s3PresignedUrlService.createGetObjectUrl(objectKey)).thenReturn(playUrl);

        SoundPlayResponse response = soundService.getPlayUrl("random-firewood", objectKey);

        assertEquals("random-firewood", response.soundId());
        assertEquals("firewood", response.categoryId());
        assertEquals(objectKey, response.objectKey());
        assertEquals(playUrl, response.playUrl());
    }

    private Sound firewoodSound() {
        return new Sound(
                "random-firewood",
                new SoundCategory(
                        "firewood",
                        "장작소리",
                        "따뜻하게 타오르는 장작의 잔잔한 소리",
                        "포근함"
                ),
                "랜덤 장작소리",
                List.of("수면", "장작", "포근함"),
                60,
                "sounds/firewood/"
        );
    }
}
