package com.lura.core.storage;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class S3PresignedUrlService {

    private final S3Presigner s3Presigner;
    private final S3Config config;

    public S3PresignedUrlService(
            S3Presigner s3Presigner,
            S3Config config
    ) {
        this.s3Presigner = s3Presigner;
        this.config = config;
    }

    public String createGetObjectUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(config.bucket())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(config.presignedUrlDuration())
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
}
