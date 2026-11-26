package com.example.oes.service;

public interface StorageService {

    /** 启动时确保 bucket 存在；失败只告警，不阻断应用启动 */
    void ensureBucket();

    /** 上传字节内容，返回 objectKey */
    String upload(String objectKey, byte[] data, String contentType);

    /** 生成下载链接（预签名，默认 15 分钟） */
    String presignedUrl(String objectKey, int minutes);
}
