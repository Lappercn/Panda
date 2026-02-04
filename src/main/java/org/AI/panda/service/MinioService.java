package org.AI.panda.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    @Qualifier("minioPresignClient")
    private MinioClient minioPresignClient;

    @Value("${panda.minio.bucket-name}")
    private String bucketName;

    /**
     * 检查并创建 Bucket
     */
    public void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Error ensuring bucket exists: {}", e.getMessage());
            throw new RuntimeException("MinIO Bucket Check Failed", e);
        }
    }

    /**
     * 上传文件
     * @param objectName 存储在 MinIO 中的文件名 (建议使用 UUID 或带路径的名称)
     * @param file 文件对象
     * @return 文件访问 URL (或只是确认上传成功)
     */
    public void uploadFile(String objectName, MultipartFile file) {
        ensureBucketExists();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Uploaded file to MinIO: {}", objectName);
        } catch (Exception e) {
            log.error("Error uploading file to MinIO: {}", e.getMessage());
            throw new RuntimeException("MinIO Upload Failed", e);
        }
    }

    /**
     * 上传文件流
     */
    public void uploadStream(String objectName, InputStream inputStream, String contentType) {
        ensureBucketExists();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, -1, 10485760) // unknown size, part size 10MB
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO Stream Upload Failed", e);
        }
    }

    /**
     * 获取文件流
     */
    public InputStream getFile(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO Get Object Failed", e);
        }
    }

    /**
     * 删除文件
     */
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", objectName);
        } catch (Exception e) {
            log.error("Error deleting file from MinIO: {}", e.getMessage());
            throw new RuntimeException("MinIO Delete Failed", e);
        }
    }

    /**
     * 获取预签名 URL (用于前端直接预览或下载)
     * @param objectName 对象名
     * @param expirySeconds 过期时间 (秒)
     * @return URL
     */
    public String getPresignedUrl(String objectName, int expirySeconds) {
        try {
            return minioPresignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO presign failed, bucket={}, objectName={}, expirySeconds={}", bucketName, objectName, expirySeconds, e);
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                throw new RuntimeException("MinIO Get URL Failed", e);
            }
            throw new RuntimeException("MinIO Get URL Failed: " + msg, e);
        }
    }

    public String getPresignedPutUrl(String objectName, int expirySeconds) {
        ensureBucketExists();
        try {
            return minioPresignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO presign put failed, bucket={}, objectName={}, expirySeconds={}", bucketName, objectName, expirySeconds, e);
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                throw new RuntimeException("MinIO Put URL Failed", e);
            }
            throw new RuntimeException("MinIO Put URL Failed: " + msg, e);
        }
    }

    public StatObjectResponse statObject(String objectName) {
        ensureBucketExists();
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO Stat Object Failed", e);
        }
    }
}
