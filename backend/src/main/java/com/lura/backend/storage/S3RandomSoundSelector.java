package com.lura.backend.storage;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class S3RandomSoundSelector {

    private static final List<String> SUPPORTED_AUDIO_EXTENSIONS = List.of(
            ".mp3",
            ".m4a",
            ".aac",
            ".wav",
            ".ogg"
    );

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3RandomSoundSelector(
            S3Client s3Client,
            S3Properties properties
    ) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public Optional<String> selectRandomAudioObjectKey(String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(properties.bucket())
                .prefix(normalizePrefix(prefix))
                .build();

        List<String> objectKeys = s3Client.listObjectsV2Paginator(request)
                .contents()
                .stream()
                .map(S3Object::key)
                .filter(this::isPlayableAudioObjectKey)
                .toList();

        if (objectKeys.isEmpty()) {
            return Optional.empty();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(objectKeys.size());
        return Optional.of(objectKeys.get(randomIndex));
    }

    public boolean isPlayableAudioObjectKey(String objectKey) {
        String lowerCaseObjectKey = objectKey.toLowerCase();
        return !lowerCaseObjectKey.endsWith("/")
                && SUPPORTED_AUDIO_EXTENSIONS.stream().anyMatch(lowerCaseObjectKey::endsWith);
    }

    private String normalizePrefix(String prefix) {
        if (prefix.endsWith("/")) {
            return prefix;
        }
        return prefix + "/";
    }
}
