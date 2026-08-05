package com.aiworkmate.service;

import java.nio.file.Path;

public interface OcrService {

    /**
     * 识别图片文本。
     *
     * <p>引擎未启用、不可用、调用失败或识别结果为空时返回 {@code null}，
     * 调用方按「图片无可用文本」处理，不得伪造识别结果。</p>
     *
     * @param imageFile 本地临时图片文件
     * @param filename  原始文件名（用于 MIME 兜底判断）
     * @return 识别文本；不可用或为空时返回 null
     */
    String recognize(Path imageFile, String filename);

    /**
     * 引擎当前是否可用（健康检查、状态展示用）。
     */
    boolean isAvailable();
}
