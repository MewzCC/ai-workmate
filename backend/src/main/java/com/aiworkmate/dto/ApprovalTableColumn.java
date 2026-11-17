package com.aiworkmate.dto;

import java.math.BigDecimal;
import java.util.List;

public record ApprovalTableColumn(String name, String label, String type, boolean required,
                                  List<String> options,
                                  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) BigDecimal min) {
    public ApprovalTableColumn { options = List.copyOf(options); }
}
