package com.yutong.system.file.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置。
 * 设计来源: 12-中间件集成设计、98-后端实现蓝图
 * 配置前缀: yutong.storage.minio (与 application-local.yml 对齐)
 */
@Configuration
public class MinioConfig {

    /**
     * 创建 MinIO 客户端 Bean。
     * 通过 yutong-infra 传递依赖引入 io.minio:minio:8.5.17。
     */
    @Bean
    public MinioClient minioClient(
            @Value("${yutong.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${yutong.storage.minio.access-key:yutong-minio}") String accessKey,
            @Value("${yutong.storage.minio.secret-key:yutong-minio-dev-secret}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 暴露桶名供 FileService 注入。
     * 默认桶名 yutong，可通过 yutong.storage.minio.bucket 覆盖。
     */
    @Bean("minioBucketName")
    public String minioBucketName(
            @Value("${yutong.storage.minio.bucket:yutong}") String bucket) {
        return bucket;
    }
}
