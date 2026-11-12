package com.aiworkmate.integration;

import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.ApprovalDraftUpdateRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.GenericApprovalService;
import com.aiworkmate.service.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApprovalApplicationAttachmentIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    private static final OwnedMinioFixture MINIO = new OwnedMinioFixture();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired GenericApprovalService approvals;
    @SpyBean ObjectStorageService storage;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner, reviewer, colleague;
    private long tenant, application;
    private String formKey;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENV, AgentBrowserFixture.reserveUiPort(), SECRET);
        MINIO.register(p);
        p.add("app.ocr.enabled", () -> "false");
        p.add("app.upload.file-max-bytes", () -> "4096");
        p.add("app.upload.image-max-bytes", () -> "1024");
    }
    @AfterAll static void closeStorage() { MINIO.close(); }

    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        owner = http.createAndLogin(tenant, List.of("route:approval-start", "approval:create", "approval:submit", "approval:reopen"));
        reviewer = http.createAndLogin(tenant, List.of("todo:read", "approval:act", "route:todo"));
        colleague = http.createAndLogin(tenant, List.of("route:approval-start", "approval:create"));
        jdbc.update("UPDATE app_user SET approver_user_id=? WHERE id=?", reviewer.id(), owner.id());
        formKey = "upload_" + UUID.randomUUID().toString().replace("-", "");
        long form = jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,?,?,'ENABLED',?) RETURNING id",
                Long.class, tenant, formKey, formKey, "{\"fields\":[{\"name\":\"proof\",\"type\":\"file\"},{\"name\":\"photo\",\"type\":\"image\"}]}", owner.id());
        jdbc.update("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status) VALUES (?,?,?,?,'[{\"nodeName\":\"Review\",\"approveType\":\"DIRECT_MANAGER\"}]','ENABLED')", tenant, formKey, formKey, form);
        application = approvals.createDraft(owner.id(), new ApprovalDraftRequest(formKey, formKey, Map.of())).id();
    }
    @AfterEach void clearFault() {
        jdbc.execute("DROP TRIGGER IF EXISTS approval_upload_fault ON business_audit_log");
        jdbc.execute("DROP FUNCTION IF EXISTS approval_upload_fault()");
    }

    @Test void uploadsBindsAndDownloadsRealStoredBytesThroughProtectedHttp() throws Exception {
        byte[] bytes = "Owned approval attachment".getBytes(StandardCharsets.UTF_8);
        var uploaded = upload(owner, application, "proof", "proof.txt", bytes);
        assertThat(uploaded.statusCode()).isEqualTo(200);
        var receipt = json.readTree(uploaded.body()).path("data");
        assertThat(receipt.path("id").isTextual()).isTrue();
        assertThat(uploaded.body()).doesNotContain("storageKey", "approval-attachments/", "127.0.0.1");
        String id = receipt.path("id").asText();
        assertThat(content(colleague, application, id, null).statusCode()).isEqualTo(403);
        assertThat(content(reviewer, application, id, null).statusCode()).isEqualTo(403);
        assertThat(content(owner, application, id, null).body()).isEqualTo(bytes);
        approvals.updateDraft(owner.id(), application, new ApprovalDraftUpdateRequest(formKey, Map.of("proof", List.of(id)), 0));
        approvals.submitDraft(owner.id(), application, new VersionRequest(1));
        long task = jdbc.queryForObject("SELECT id FROM workflow_task WHERE business_type='GENERIC_APPROVAL' AND business_id=?", Long.class, application);
        var downloaded = content(reviewer, application, id, task);
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(bytes);
        assertThat(downloaded.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(downloaded.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;");
        assertThat(upload(owner, application, "proof", "another.txt", bytes).statusCode()).isEqualTo(409);
    }

    @Test void enforcesAttachmentFieldTypeSizeOwnershipAndCrossTenantBoundaries() throws Exception {
        byte[] text = "Owned file".getBytes(StandardCharsets.UTF_8);
        assertThat(upload(owner, application, "photo", "proof.txt", text).statusCode()).isEqualTo(400);
        assertThat(upload(owner, application, "unknown", "proof.txt", text).statusCode()).isEqualTo(400);
        assertThat(upload(owner, application, "proof", "large.txt", new byte[4097]).statusCode()).isEqualTo(400);
        assertThat(upload(colleague, application, "proof", "proof.txt", text).statusCode()).isEqualTo(404);
        long foreign = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign tenant') RETURNING id", Long.class,
                "UPLOAD_" + UUID.randomUUID().toString().substring(0, 16));
        var outsider = http.createAndLogin(foreign, List.of("route:approval-start", "approval:create"));
        assertThat(upload(outsider, application, "proof", "proof.txt", text).statusCode()).isEqualTo(404);
        String id = json.readTree(upload(owner, application, "proof", "proof.txt", text).body()).at("/data/id").asText();
        assertThat(content(outsider, application, id, null).statusCode()).isEqualTo(404);
        long otherDraft = approvals.createDraft(colleague.id(), new ApprovalDraftRequest(formKey, formKey, Map.of())).id();
        assertThatThrownBy(() -> approvals.updateDraft(colleague.id(), otherDraft,
                new ApprovalDraftUpdateRequest(formKey, Map.of("proof", List.of(id)), 0)))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class).extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }

    @Test void imageUploadUsesValidatedMimeWithoutRequiringOcr() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jFoQAAAAASUVORK5CYII=");
        var uploaded = upload(owner, application, "photo", "proof.png", png);
        assertThat(uploaded.statusCode()).isEqualTo(200);
        assertThat(json.readTree(uploaded.body()).at("/data/mimeType").asText()).isEqualTo("image/png");
    }

    @Test void auditFailureRollsBackTheRowAndRemovesTheStoredObject() throws Exception {
        clearInvocations(storage);
        jdbc.execute("CREATE FUNCTION approval_upload_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'owned upload audit fault'; END $$");
        jdbc.execute("CREATE TRIGGER approval_upload_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION approval_upload_fault()");
        assertThat(upload(owner, application, "proof", "proof.txt", "Owned file".getBytes(StandardCharsets.UTF_8)).statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application_attachment WHERE application_id=?", Integer.class, application)).isZero();
        var key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(), anyLong(), anyString());
        assertThat(storage.exists(key.getValue())).isFalse();
    }

    private HttpResponse<String> upload(AgentHttpTestFixture.Actor actor, long applicationId, String field, String filename, byte[] bytes) throws Exception {
        String boundary = "OwnedBoundary" + UUID.randomUUID().toString().replace("-", "");
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"fieldName\"\r\n\r\n" + field
                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes); body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = http.request("POST", "/api/approval-applications/" + applicationId + "/attachments", actor, null, null)
                .setHeader("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        return AgentHttpTestFixture.client().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> content(AgentHttpTestFixture.Actor actor, long applicationId, String id, Long taskId) throws Exception {
        String path = "/api/approval-applications/" + applicationId + "/attachments/" + id + "/content" + (taskId == null ? "" : "?taskId=" + taskId);
        return AgentHttpTestFixture.client().send(http.request("GET", path, actor, null, null).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
