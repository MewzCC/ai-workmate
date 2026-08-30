package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.MinioProperties;
import com.aiworkmate.service.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 MinIO 的对象存储实现。
 * <p>客户端在构造时即建立（无网络调用），桶在首次写入前惰性创建，
 * 避免后端启动期强依赖 MinIO 在线。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService implements ObjectStorageService {

    private final MinioProperties properties;

    private MinioClient client;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    @PostConstruct
    void initClient() {
        String region = properties.getRegion() == null || properties.getRegion().isBlank()
                ? null : properties.getRegion();
        this.client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(region)
                .build();
        log.info("MinIO client initialized, endpoint={}, bucket={}",
                properties.getEndpoint(), properties.getBucket());
    }

    @Override
    public void store(String key, InputStream content, long size, String contentType) {
        ensureBucket();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(content, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            log.error("MinIO putObject failed, key={}", key, ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "对象存储写入失败");
        }
    }

    @Override
    public Resource load(String key) {
        return new MinioObjectResource(key);
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception ex) {
            // 不存在视为成功，其它错误仅记录，避免删除流程中断
            log.warn("MinIO removeObject failed, key={}", key, ex);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
            return true;
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) return false;
            log.warn("MinIO statObject failed, key={}", key, ex);
            return false;
        } catch (Exception ex) {
            log.warn("MinIO statObject failed, key={}", key, ex);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            return exists || properties.isAutoCreateBucket();
        } catch (Exception ex) {
            log.warn("MinIO capability inspection failed, cause={}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private void ensureBucket() {
        if (bucketReady.get()) return;
        if (!properties.isAutoCreateBucket()) {
            bucketReady.set(true);
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucket())
                        .build());
                log.info("MinIO bucket created, bucket={}", properties.getBucket());
            }
            bucketReady.set(true);
        } catch (Exception ex) {
            log.error("MinIO bucket ensure failed, bucket={}", properties.getBucket(), ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "对象存储不可用");
        }
    }

    /**
     * 惰性资源：每次读取都打开新的 MinIO 输入流，支持多次读取与流式响应。
     */
    private final class MinioObjectResource extends AbstractResource {

        private final String key;

        private MinioObjectResource(String key) {
            this.key = key;
        }

        @Override
        public String getDescription() {
            return "MinIO object [" + properties.getBucket() + "/" + key + "]";
        }

        @Override
        public InputStream getInputStream() {
            try {
                GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(key)
                        .build());
                return response;
            } catch (Exception ex) {
                log.error("MinIO getObject failed, key={}", key, ex);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "对象存储读取失败");
            }
        }

        @Override
        public long contentLength() {
            try {
                StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(key)
                        .build());
                return stat.size();
            } catch (Exception ex) {
                return -1;
            }
        }
    }
}
