package com.aiworkmate.agent.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record PlannerCandidate(String summary, List<Step> steps) {
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Step(String toolCode, JsonNode arguments) { }
}
