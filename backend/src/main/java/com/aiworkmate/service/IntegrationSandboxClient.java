package com.aiworkmate.service;
import com.aiworkmate.dto.IntegrationOptionsResponse;
import com.aiworkmate.service.model.SandboxCallResult;
import java.util.List;
public interface IntegrationSandboxClient {
 List<IntegrationOptionsResponse.Option> options();
 boolean isRegistered(String upstreamCode);
 boolean isAvailable(String upstreamCode);
 SandboxCallResult execute(String upstreamCode,String method,String relativePath,String body);
}
