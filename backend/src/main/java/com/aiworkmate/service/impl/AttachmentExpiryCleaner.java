package com.aiworkmate.service.impl;

import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.entity.Attachment;
import com.aiworkmate.mapper.AttachmentMapper;
import com.aiworkmate.service.ObjectStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天附件过期清理：按配置的最长保留天数删除超期附件（MinIO 对象 + 数据库记录）。
 * <p>保留天数由 {@code app.upload.attachment-max-age-days} 控制，小于等于 0 时不启用；
 * 执行时间由 {@code app.upload.attachment-cleanup-cron} 控制（默认每天 03:30）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentExpiryCleaner {

    /** 单批扫描上限，避免一次加载过多记录 */
    private static final int BATCH_SIZE = 100;

    private final AttachmentMapper attachmentMapper;
    private final ObjectStorageService objectStorageService;
    private final UploadProperties properties;

    @Scheduled(cron = "${app.upload.attachment-cleanup-cron}")
    public void cleanExpiredAttachments() {
        int maxAgeDays = properties.getAttachmentMaxAgeDays();
        if (maxAgeDays <= 0) {
            log.debug("Attachment expiry cleanup disabled, attachment-max-age-days={}", maxAgeDays);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(maxAgeDays);
        int deleted = 0;
        int scanned = 0;
        boolean more = true;
        while (more) {
            List<Attachment> batch = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                    .lt(Attachment::getCreatedAt, cutoff)
                    .isNotNull(Attachment::getStorageName)
                    .orderByAsc(Attachment::getId)
                    .last("LIMIT " + BATCH_SIZE));
            more = batch.size() == BATCH_SIZE;
            for (Attachment attachment : batch) {
                scanned++;
                try {
                    objectStorageService.delete(attachment.getStorageName());
                    attachmentMapper.deleteById(attachment.getId());
                    deleted++;
                } catch (Exception ex) {
                    // 单条失败不中断整轮清理
                    log.warn("Attachment expiry cleanup failed, attachmentId={}, storageName={}",
                            attachment.getId(), attachment.getStorageName(), ex);
                }
            }
        }
        if (deleted > 0) {
            log.info("Attachment expiry cleanup done, cutoff={}, scanned={}, deleted={}", cutoff, scanned, deleted);
        } else {
            log.debug("Attachment expiry cleanup done, cutoff={}, scanned={}, deleted={}", cutoff, scanned, deleted);
        }
    }
}
