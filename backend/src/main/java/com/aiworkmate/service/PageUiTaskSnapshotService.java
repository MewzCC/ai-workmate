package com.aiworkmate.service;

import com.aiworkmate.agent.task.AgentTaskMapper;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns task persistence and frozen-context parsing behind a transport-neutral facade. */
@Service
@RequiredArgsConstructor
public class PageUiTaskSnapshotService {
    private final AgentTaskMapper tasks;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Snapshot runningSnapshot(Long tenantId, Long userId, Long taskId) {
        if (tenantId == null || userId == null || taskId == null)
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        var task = tasks.selectOwnedRunningSnapshot(tenantId, userId, taskId);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        try {
            var snapshot = mapper.reader()
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(task.getPageContext());
            if (snapshot == null || !snapshot.isObject()) throw new BusinessException(ErrorCode.REQUEST_INVALID);
            if (!snapshot.has("uiFormSession")) {
                if (snapshot.has("uiRecordId") || snapshot.has("uiRecordVersion"))
                    throw new BusinessException(ErrorCode.REQUEST_INVALID);
                return new Snapshot(task.getPageId(), null, null, null);
            }
            var session = snapshot.path("uiFormSession");
            var record = snapshot.get("uiRecordId");
            var version = snapshot.get("uiRecordVersion");
            if (!session.isTextual() || !session.asText().matches("^[a-zA-Z0-9_-]{16,64}$")
                    || (record == null) != (version == null)
                    || record != null && (!record.isIntegralNumber() || !record.canConvertToLong() || record.asLong() < 1
                    || !version.isIntegralNumber() || !version.canConvertToInt() || version.asInt() < 0 || version.asInt() > 2147483646))
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            return new Snapshot(task.getPageId(), session.asText(), record == null ? null : record.longValue(), version == null ? null : version.intValue());
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }
    public record Snapshot(String pageId, String formSession, Long recordId, Integer version) {}
}
