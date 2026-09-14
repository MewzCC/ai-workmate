package com.aiworkmate.mapper;

import com.aiworkmate.dto.SandboxReplayBaselineResponse;
import com.aiworkmate.entity.IntegrationReplayJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IntegrationReplayJobMapper extends BaseMapper<IntegrationReplayJob> {

    @Select({
            "<script>",
            "SELECT invocation.id AS invocationId, endpoint.id AS endpointId,",
            "endpoint.endpoint_code AS endpointCode, endpoint.name AS endpointName,",
            "endpoint.http_method AS method, endpoint.relative_path AS relativePath,",
            "invocation.outcome, invocation.http_status AS httpStatus,",
            "invocation.duration_ms AS durationMs, invocation.operator_label AS operatorLabel,",
            "invocation.created_at AS createdAt",
            "FROM integration_invocation invocation",
            "JOIN integration_endpoint endpoint ON endpoint.id = invocation.endpoint_id",
            " AND endpoint.tenant_id = invocation.tenant_id AND endpoint.deleted = FALSE",
            "WHERE invocation.tenant_id = #{tenantId}",
            "<if test='keyword != null'> AND (endpoint.endpoint_code ILIKE CONCAT('%', #{keyword}, '%')",
            " OR endpoint.name ILIKE CONCAT('%', #{keyword}, '%')",
            " OR endpoint.relative_path ILIKE CONCAT('%', #{keyword}, '%'))</if>",
            "ORDER BY invocation.created_at DESC, invocation.id DESC LIMIT #{limit}",
            "</script>"
    })
    List<SandboxReplayBaselineResponse> selectBaselines(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    @Insert("""
            INSERT INTO integration_replay_job(
                tenant_id, source_invocation_id, endpoint_id, endpoint_code, endpoint_name,
                http_method, relative_path, baseline_request_hash, baseline_outcome,
                baseline_http_status, baseline_response_hash, baseline_response_preview,
                status, trace_id, reason, idempotency_key, requested_by, requested_by_label,
                started_at)
            VALUES(
                #{tenantId}, #{sourceInvocationId}, #{endpointId}, #{endpointCode}, #{endpointName},
                #{httpMethod}, #{relativePath}, #{baselineRequestHash}, #{baselineOutcome},
                #{baselineHttpStatus}, #{baselineResponseHash}, #{baselineResponsePreview},
                #{status}, #{traceId}, #{reason}, #{idempotencyKey}, #{requestedBy},
                #{requestedByLabel}, #{startedAt})
            ON CONFLICT (tenant_id, requested_by, idempotency_key) DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertReservation(IntegrationReplayJob job);
}
