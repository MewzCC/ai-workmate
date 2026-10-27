package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class AssetUpdateToolHandler extends TypedWriteToolHandler<AssetToolPort.Update, AssetToolPort.MetadataReceipt> {
    private final AssetToolPort port;
    public AssetUpdateToolHandler(AssetToolPort port, ObjectMapper mapper) {
        super(ToolCode.ASSET_UPDATE, mapper); this.port = port;
    }
    @Override protected AssetToolPort.Update parseArguments(JsonNode args) {
        var fields = AssetMetadataToolArguments.fields(args, false);
        boolean clear = Boolean.TRUE.equals(optionalBoolean(args, "clearPurchaseDate"));
        if ((clear && fields.purchaseDate() != null) || (!clear && fields.assetCode() == null && fields.name() == null
                && fields.category() == null && fields.specification() == null && fields.purchaseDate() == null
                && fields.originalValue() == null && fields.remark() == null)) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return new AssetToolPort.Update(requiredLong(args, "assetId", 1), requiredInt(args, "version", 0, Integer.MAX_VALUE - 1), fields, clear);
    }
    @Override protected AssetToolPort.MetadataReceipt invoke(TrustedToolContext context, AssetToolPort.Update command) {
        return port.update(context.actor(), command);
    }
}
