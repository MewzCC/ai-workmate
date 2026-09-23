package com.aiworkmate.mapper;

import com.aiworkmate.dto.RuntimeLogDetailResponse;
import com.aiworkmate.dto.RuntimeLogRecordResponse;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import com.aiworkmate.dto.PlatformObservabilityResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RuntimeLogMapper {

    @Select("""
            SELECT date_trunc(#{bucket}, started_at) AS bucket, source,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID')) AS failed,
                   COUNT(*) FILTER (WHERE outcome = 'REJECTED' OR decision IN ('DENY', 'STALE', 'THROTTLED', 'UNAVAILABLE')) AS blocked
            FROM runtime_log_view
            WHERE tenant_id = #{tenantId} AND started_at >= #{from} AND started_at <= #{to}
            GROUP BY date_trunc(#{bucket}, started_at), source
            ORDER BY bucket, source
            """)
    List<PlatformObservabilityResponse.TimelinePoint> selectTimeline(
            @Param("tenantId") Long tenantId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("bucket") String bucket);

    @Select("""
            SELECT source AS code, COUNT(*) AS total FROM runtime_log_view
            WHERE tenant_id = #{tenantId} AND started_at >= #{from} AND started_at <= #{to}
            GROUP BY source ORDER BY total DESC
            """)
    List<PlatformObservabilityResponse.CategoryCount> selectSourceCounts(
            @Param("tenantId") Long tenantId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT error_code AS code, COUNT(*) AS total FROM runtime_log_view
            WHERE tenant_id = #{tenantId} AND started_at >= #{from} AND started_at <= #{to}
              AND outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID', 'REJECTED')
              AND error_code IS NOT NULL AND error_code <> ''
            GROUP BY error_code ORDER BY total DESC, error_code LIMIT 8
            """)
    List<PlatformObservabilityResponse.CategoryCount> selectTopErrorCodes(
            @Param("tenantId") Long tenantId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT CAST(COALESCE(ROUND((percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms))::numeric), 0) AS BIGINT)
            FROM runtime_log_view
            WHERE tenant_id = #{tenantId} AND started_at >= #{from} AND started_at <= #{to}
              AND duration_ms IS NOT NULL
            """)
    Long selectP95Duration(@Param("tenantId") Long tenantId,
                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select({
            "<script>",
            "SELECT source, log_id AS id, reference_code AS referenceCode, operation, outcome, decision,",
            "status_code AS statusCode, duration_ms AS durationMs, operator_label AS operatorLabel,",
            "trace_id AS traceId, error_code AS errorCode, actor_type AS actorType, event_type AS eventType,",
            "started_at AS startedAt, completed_at AS completedAt",
            "FROM runtime_log_view",
            "WHERE tenant_id = #{tenantId}",
            "<if test='source != null'> AND source = #{source}</if>",
            "<if test='outcome != null'> AND outcome = #{outcome}</if>",
            "<if test='group == \"FAILED\"'> AND outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID')</if>",
            "<if test='group == \"BLOCKED\"'> AND (outcome = 'REJECTED' OR decision IN ('DENY', 'STALE', 'THROTTLED', 'UNAVAILABLE'))</if>",
            "<if test='errorCode != null'> AND error_code = #{errorCode} AND outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID', 'REJECTED')</if>",
            "<if test='keyword != null'> AND (reference_code ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operation ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operator_label ILIKE CONCAT('%', #{keyword}, '%')",
            " OR trace_id ILIKE CONCAT('%', #{keyword}, '%')",
            " OR error_code ILIKE CONCAT('%', #{keyword}, '%'))</if>",
            "AND started_at &gt;= #{from}",
            "<choose><when test='toExclusive'> AND started_at &lt; #{to}</when><otherwise> AND started_at &lt;= #{to}</otherwise></choose>",
            "ORDER BY started_at DESC, source, log_id DESC",
            "LIMIT #{size} OFFSET #{offset}",
            "</script>"
    })
    List<RuntimeLogRecordResponse> selectPage(
            @Param("tenantId") Long tenantId,
            @Param("source") String source,
            @Param("outcome") String outcome,
            @Param("group") String group,
            @Param("errorCode") String errorCode,
            @Param("keyword") String keyword,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("toExclusive") boolean toExclusive,
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
            "<if test='group == \"FAILED\"'> AND outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID')</if>",
            "<if test='group == \"BLOCKED\"'> AND (outcome = 'REJECTED' OR decision IN ('DENY', 'STALE', 'THROTTLED', 'UNAVAILABLE'))</if>",
            "<if test='errorCode != null'> AND error_code = #{errorCode} AND outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID', 'REJECTED')</if>",
            "<if test='keyword != null'> AND (reference_code ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operation ILIKE CONCAT('%', #{keyword}, '%')",
            " OR operator_label ILIKE CONCAT('%', #{keyword}, '%')",
            " OR trace_id ILIKE CONCAT('%', #{keyword}, '%')",
            " OR error_code ILIKE CONCAT('%', #{keyword}, '%'))</if>",
            "AND started_at &gt;= #{from}",
            "<choose><when test='toExclusive'> AND started_at &lt; #{to}</when><otherwise> AND started_at &lt;= #{to}</otherwise></choose>",
            "</script>"
    })
    RuntimeLogStatsResponse selectStats(
            @Param("tenantId") Long tenantId,
            @Param("source") String source,
            @Param("outcome") String outcome,
            @Param("group") String group,
            @Param("errorCode") String errorCode,
            @Param("keyword") String keyword,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("toExclusive") boolean toExclusive);

    @Select("""
            SELECT source, log_id AS id, reference_code AS referenceCode, operation, outcome,
                   decision, decision_code AS decisionCode, status_code AS statusCode,
                   duration_ms AS durationMs, operator_label AS operatorLabel, trace_id AS traceId,
                   request_fingerprint AS requestFingerprint, detail_preview AS detailPreview,
                   error_code AS errorCode, handler_invoked AS handlerInvoked,
                   result_bytes AS resultBytes, attempt, actor_type AS actorType,
                   event_type AS eventType, client_ip AS clientIp, user_agent AS userAgent,
                   started_at AS startedAt,
                   completed_at AS completedAt
            FROM runtime_log_view
            WHERE tenant_id = #{tenantId} AND source = #{source} AND log_id = #{id}
            """)
    RuntimeLogDetailResponse selectDetail(@Param("tenantId") Long tenantId,
                                           @Param("source") String source,
                                           @Param("id") Long id);
}
