package com.lura.lambda;

import com.lura.core.api.SoundPlayResponse;
import com.lura.core.catalog.SoundCatalogItem;
import com.lura.core.storage.S3PresignedUrlService;
import com.lura.core.storage.S3RandomSoundSelector;

final class S3PlaybackUrlProvider implements PlaybackUrlProvider {

    private final S3RandomSoundSelector s3RandomSoundSelector;
    private final S3PresignedUrlService s3PresignedUrlService;

    S3PlaybackUrlProvider(
            S3RandomSoundSelector s3RandomSoundSelector,
            S3PresignedUrlService s3PresignedUrlService
    ) {
        this.s3RandomSoundSelector = s3RandomSoundSelector;
        this.s3PresignedUrlService = s3PresignedUrlService;
    }

    @Override
    public SoundPlayResponse getPlayUrl(SoundCatalogItem sound, String objectKey) {
        String selectedObjectKey = resolvePlaybackObjectKey(sound, objectKey);
        return new SoundPlayResponse(
                sound.id(),
                sound.categoryId(),
                selectedObjectKey,
                s3PresignedUrlService.createGetObjectUrl(selectedObjectKey)
        );
    }

    private String resolvePlaybackObjectKey(SoundCatalogItem sound, String objectKey) {
        if (objectKey != null && !objectKey.isBlank()) {
            validateObjectKeyBelongsToSound(sound, objectKey);
            return objectKey;
        }

        return s3RandomSoundSelector.selectRandomAudioObjectKey(sound.s3Prefix())
                .orElseThrow(() -> new ApiException(404, "No playable S3 object found for category"));
    }

    private void validateObjectKeyBelongsToSound(SoundCatalogItem sound, String objectKey) {
        if (!objectKey.startsWith(sound.s3Prefix())
                || !s3RandomSoundSelector.isExistingPlayableAudioObjectKey(objectKey)) {
            throw new ApiException(400, "Invalid playback object key");
        }
    }
}
