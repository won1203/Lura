package com.lura.backend.storage;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3PresignedUrlService {

    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3PresignedUrlService(
            S3Presigner s3Presigner,
            S3Properties properties
    ) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public String createGetObjectUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlDuration())
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
}
