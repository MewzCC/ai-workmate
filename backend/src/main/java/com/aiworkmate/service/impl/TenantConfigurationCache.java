package com.aiworkmate.service.impl;

import com.aiworkmate.entity.TenantConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantConfigurationCache {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "oa:tenant-configuration:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<TenantConfiguration> get(Long tenantId) {
        try {
            String value = redisTemplate.opsForValue().get(key(tenantId));
            return value == null ? Optional.empty() : Optional.of(objectMapper.readValue(value, TenantConfiguration.class));
        } catch (RuntimeException | JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public void put(Long tenantId, TenantConfiguration configuration) {
        try {
            redisTemplate.opsForValue().set(key(tenantId), objectMapper.writeValueAsString(configuration), TTL);
        } catch (RuntimeException | JsonProcessingException ignored) {
            // Redis 仅用于读性能优化；不可用时由数据库作为唯一事实来源。
        }
    }

    public void evict(Long tenantId) {
        try {
            redisTemplate.delete(key(tenantId));
        } catch (RuntimeException ignored) {
            // 写事务不依赖缓存可用性，失败时下一次读取仍会回源数据库。
        }
    }

    private String key(Long tenantId) {
        return PREFIX + tenantId;
    }
}
