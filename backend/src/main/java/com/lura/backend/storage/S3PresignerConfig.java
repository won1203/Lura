package com.lura.backend.storage;

import com.lura.core.storage.S3Config;
import com.lura.core.storage.S3PresignedUrlService;
import com.lura.core.storage.S3RandomSoundSelector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3PresignerConfig {

    @Bean
    public S3Config s3Config(S3Properties properties) {
        return new S3Config(
                properties.bucket(),
                properties.region(),
                properties.presignedUrlDuration()
        );
    }

    @Bean
    public S3Presigner s3Presigner(S3Config config) {
        return S3Presigner.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Client s3Client(S3Config config) {
        return S3Client.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3PresignedUrlService s3PresignedUrlService(
            S3Presigner s3Presigner,
            S3Config config
    ) {
        return new S3PresignedUrlService(s3Presigner, config);
    }

    @Bean
    public S3RandomSoundSelector s3RandomSoundSelector(
            S3Client s3Client,
            S3Config config
    ) {
        return new S3RandomSoundSelector(s3Client, config);
    }
}
