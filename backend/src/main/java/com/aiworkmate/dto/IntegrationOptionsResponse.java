package com.aiworkmate.dto;
import java.util.List;
public record IntegrationOptionsResponse(List<Option> upstreams){public record Option(String code,String label,boolean available) {}}
