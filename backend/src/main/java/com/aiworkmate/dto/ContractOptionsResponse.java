package com.aiworkmate.dto;

import java.util.List;

public record ContractOptionsResponse(
        List<ContractOptionResponse> owners,
        List<ContractOptionResponse> suppliers
) {
}
