package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.service.EmbeddingProviderClient;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.EmbeddingResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConfigurableEmbeddingServiceImpl implements EmbeddingService {

    private static final int STORAGE_DIMENSION = 1024;

    private final EmbeddingProperties properties;
    private final Map<String, EmbeddingProviderClient> clients;

    public ConfigurableEmbeddingServiceImpl(
            EmbeddingProperties properties,
            List<EmbeddingProviderClient> clients
    ) {
        this.properties = properties;
        this.clients = clients.stream().collect(Collectors.toUnmodifiableMap(
                client -> client.provider().toLowerCase(Locale.ROOT),
                Function.identity()
        ));
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE, "向量模型能力未启用");
        }
        return selectedClient().embed(texts);
    }

    @Override
    public EmbeddingDescriptor current() {
        EmbeddingProviderClient client = selectedClient();
        return new EmbeddingDescriptor(client.provider(), client.model(), properties.getDimension());
    }

    private EmbeddingProviderClient selectedClient() {
        if (properties.getDimension() != STORAGE_DIMENSION) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "EMBEDDING_DIMENSION 必须与数据库 vector(1024) 保持一致");
        }
        String provider = properties.getProvider() == null
                ? "" : properties.getProvider().strip().toLowerCase(Locale.ROOT);
        EmbeddingProviderClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "不支持的向量模型 Provider：" + provider);
        }
        return client;
    }
}
