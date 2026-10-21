package com.aiworkmate.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Omitted metadata stays unchanged in a patch; optional empty text explicitly clears it. */
public record AssetAgentMetadataFields(String assetCode, String name, String category, String specification,
                                       LocalDate purchaseDate, BigDecimal originalValue, String remark) { }
