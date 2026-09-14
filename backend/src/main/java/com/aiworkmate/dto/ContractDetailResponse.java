package com.aiworkmate.dto;

import java.util.List;

public record ContractDetailResponse(
        ContractResponse contract,
        List<ContractEventResponse> events
) {
}
