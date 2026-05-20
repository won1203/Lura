package com.lura.backend.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "lura.aws.s3")
public record S3Properties(
        @NotBlank String bucket,
        @NotBlank String region,
        @Min(1) @Max(10080) long presignedUrlDurationMinutes
) {
    public Duration presignedUrlDuration() {
        return Duration.ofMinutes(presignedUrlDurationMinutes);
    }
}
