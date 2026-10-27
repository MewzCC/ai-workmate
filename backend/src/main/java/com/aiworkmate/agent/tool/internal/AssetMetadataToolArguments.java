package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AssetToolPort.Fields;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

final class AssetMetadataToolArguments {
    private AssetMetadataToolArguments() {}
    static Fields fields(JsonNode args, boolean create) {
        return new Fields(create ? requiredText(args, "assetCode") : optionalTextPreservingEmpty(args, "assetCode"),
                create ? requiredText(args, "name") : optionalTextPreservingEmpty(args, "name"),
                create ? requiredText(args, "category") : optionalTextPreservingEmpty(args, "category"),
                optionalTextPreservingEmpty(args, "specification"), optionalDate(args, "purchaseDate"),
                optionalDecimal(args, "originalValue", BigDecimal.ZERO, new BigDecimal("999999999999.99"), 2),
                optionalTextPreservingEmpty(args, "remark"));
    }
}
