package com.aiworkmate.mapper;

import com.aiworkmate.dto.RuntimeLogDetailResponse;
import com.aiworkmate.dto.RuntimeLogRecordResponse;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RuntimeLogMapper {

    @Select({
            "<script>",
            "SELECT source, log_id AS id, reference_code AS referenceCode, operation, outcome, decision,",
            "status_code AS statusCode, duration_ms AS durationMs, operator_label AS operatorLabel,",
            "trace_id AS traceId, error_code AS errorCode, started_at AS startedAt, completed_at AS completedAt",
            "FROM runtime_log_view",
            "WHERE tenant_id = #{tenantId}",
            "<if test='source != null'> AND source = #{source}</if>",
            "<if test='outcome != null'> AND outcome = #{outcome}</if>",
            "<if test='keyword != null'> AND (reference_code ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operation ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operator_label ILIKE CONCAT('%', #{keyword}, '%')",
            " OR trace_id ILIKE CONCAT('%', #{keyword}, '%')",
            " OR error_code ILIKE CONCAT('%', #{keyword}, '%'))</if>",
            "AND started_at &gt;= #{from} AND started_at &lt;= #{to}",
            "ORDER BY started_at DESC, source, log_id DESC",
            "LIMIT #{size} OFFSET #{offset}",
            "</script>"
    })
    List<RuntimeLogRecordResponse> selectPage(
            @Param("tenantId") Long tenantId,
            @Param("source") String source,
            @Param("outcome") String outcome,
            @Param("keyword") String keyword,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(*) AS total,",
            "COUNT(*) FILTER (WHERE outcome = 'SUCCEEDED') AS succeeded,",
            "COUNT(*) FILTER (WHERE outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID')) AS failed,",
            "COUNT(*) FILTER (WHERE outcome = 'REJECTED' OR decision IN ('DENY', 'STALE', 'THROTTLED', 'UNAVAILABLE')) AS blocked,",
            "CAST(COALESCE(ROUND(AVG(duration_ms)), 0) AS BIGINT) AS averageDurationMs",
            "FROM runtime_log_view",
            "WHERE tenant_id = #{tenantId}",
            "<if test='source != null'> AND source = #{source}</if>",
            "<if test='outcome != null'> AND outcome = #{outcome}</if>",
            "<if test='keyword != null'> AND (reference_code ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operation ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operator_label ILIKE CONCAT('%', #{keyword}, '%')",
            " OR trace_id ILIKE CONCAT('%', #{keyword}, '%')",
            " OR error_code ILIKE CONCAT('%', #{keyword}, '%'))</if>",
            "AND started_at &gt;= #{from} AND started_at &lt;= #{to}",
            "</script>"
    })
    RuntimeLogStatsResponse selectStats(
            @Param("tenantId") Long tenantId,
            @Param("source") String source,
            @Param("outcome") String outcome,
            @Param("keyword") String keyword,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Select("""
            SELECT source, log_id AS id, reference_code AS referenceCode, operation, outcome,
                   decision, decision_code AS decisionCode, status_code AS statusCode,
                   duration_ms AS durationMs, operator_label AS operatorLabel, trace_id AS traceId,
                   request_fingerprint AS requestFingerprint, detail_preview AS detailPreview,
                   error_code AS errorCode, handler_invoked AS handlerInvoked,
                   result_bytes AS resultBytes, attempt, started_at AS startedAt,
                   completed_at AS completedAt
            FROM runtime_log_view
            WHERE tenant_id = #{tenantId} AND source = #{source} AND log_id = #{id}
            """)
    RuntimeLogDetailResponse selectDetail(@Param("tenantId") Long tenantId,
                                           @Param("source") String source,
                                           @Param("id") Long id);
}
