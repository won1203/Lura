package com.lura.core.storage;

import java.time.Duration;

public record S3Config(
        String bucket,
        String region,
        Duration presignedUrlDuration
) {
}
