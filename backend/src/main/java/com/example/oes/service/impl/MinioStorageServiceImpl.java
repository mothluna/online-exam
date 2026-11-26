package com.example.oes.service.impl;

import com.example.oes.common.exception.BizException;
import com.example.oes.common.result.ResultCode;
import com.example.oes.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

/**
 * 对象存储访问。协议层是 S3，客户端用 MinIO SDK，
 * 因此运行环境换成 MinIO / RustFS / 其他 S3 兼容实现都不用改代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageServiceImpl implements StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @PostConstruct
    @Override
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("bucket created: {}", bucket);
            }
        } catch (Exception e) {
            // 存储不可用不应阻断启动：导入/导出会各自报错，其它功能不受影响
            log.error("ensure bucket failed: {}", e.getMessage());
        }
    }

    @Override
    public String upload(String objectKey, byte[] data, String contentType) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, data.length, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            log.error("upload failed: key={}", objectKey, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件存储不可用，请稍后重试");
        }
    }

    @Override
    public String presignedUrl(String objectKey, int minutes) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(minutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("presign failed: key={}", objectKey, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "生成下载链接失败");
        }
    }
}
