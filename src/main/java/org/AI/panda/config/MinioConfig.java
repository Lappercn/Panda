package org.AI.panda.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.net.URI;

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
        String presignEndpoint = normalizeEndpoint((publicEndpoint == null || publicEndpoint.isBlank()) ? endpoint : publicEndpoint);
        return MinioClient.builder()
                .endpoint(presignEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    private String normalizeEndpoint(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isBlank()) return s;

        if (!s.contains("://")) {
            s = "https://" + s;
        }
        URI uri = URI.create(s);
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return s;
        }
        int port = uri.getPort();
        if (port > 0) {
            return scheme + "://" + host + ":" + port;
        }
        return scheme + "://" + host;
    }
}
