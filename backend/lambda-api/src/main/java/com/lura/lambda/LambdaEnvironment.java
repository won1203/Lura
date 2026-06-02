package com.lura.lambda;

import com.lura.core.storage.S3Config;

import java.time.Duration;
import java.util.Map;

final class LambdaEnvironment {

    private static final String DEFAULT_REGION = "ap-northeast-2";
    private static final long DEFAULT_PRESIGNED_URL_DURATION_MINUTES = 720L;

    private LambdaEnvironment() {
    }

    static S3Config s3Config(Map<String, String> environment) {
        return new S3Config(
                required(environment, "LURA_S3_BUCKET"),
                firstNonBlank(
                        environment.get("AWS_REGION"),
                        environment.get("AWS_DEFAULT_REGION"),
                        environment.get("LURA_AWS_REGION"),
                        DEFAULT_REGION
                ),
                Duration.ofMinutes(presignedUrlDurationMinutes(environment))
        );
    }

    private static long presignedUrlDurationMinutes(Map<String, String> environment) {
        String configured = environment.get("LURA_S3_PRESIGNED_URL_DURATION_MINUTES");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PRESIGNED_URL_DURATION_MINUTES;
        }
        try {
            long minutes = Long.parseLong(configured);
            if (minutes < 1 || minutes > 10080) {
                throw new ApiException(500, "Invalid LURA_S3_PRESIGNED_URL_DURATION_MINUTES.");
            }
            return minutes;
        } catch (NumberFormatException exception) {
            throw new ApiException(500, "Invalid LURA_S3_PRESIGNED_URL_DURATION_MINUTES.");
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(500, "Missing required environment variable: " + name);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("At least one fallback value is required.");
    }
}
