package org.AI.panda.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MinioConfig {

    @Value("${panda.minio.endpoint}")
    private String endpoint;

    @Value("${panda.minio.public-endpoint:}")
    private String publicEndpoint;

    @Value("${panda.minio.access-key}")
    private String accessKey;

    @Value("${panda.minio.secret-key}")
    private String secretKey;

    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean("minioPresignClient")
    public MinioClient minioPresignClient() {
        String presignEndpoint = (publicEndpoint == null || publicEndpoint.isBlank()) ? endpoint : publicEndpoint;
        return MinioClient.builder()
                .endpoint(presignEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
