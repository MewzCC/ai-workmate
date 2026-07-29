package com.aiworkmate.service;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * 对象存储抽象，屏蔽底层实现（MinIO）。
 * <p>所有附件、头像等文件统一通过该接口写入与读取，鉴权仍由后端接口保证。</p>
 */
public interface ObjectStorageService {

    /**
     * 上传对象。
     *
     * @param key         对象 key（全路径，需保证唯一）
     * @param content     输入流
     * @param size        内容字节数，未知时传 -1
     * @param contentType MIME 类型
     */
    void store(String key, InputStream content, long size, String contentType);

    /**
     * 加载对象为 Spring {@link Resource}，每次 {@link Resource#getInputStream()} 都会打开新流。
     */
    Resource load(String key);

    /** 删除对象，不存在时视为成功。 */
    void delete(String key);

    /** 对象是否存在。 */
    boolean exists(String key);
}
