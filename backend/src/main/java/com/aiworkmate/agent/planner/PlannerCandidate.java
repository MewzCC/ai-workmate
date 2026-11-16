package com.aiworkmate.agent.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record PlannerCandidate(String summary, List<Step> steps, String clarification) {
    public PlannerCandidate(String summary, List<Step> steps) { this(summary, steps, null); }

    public boolean requiresClarification() { return clarification != null && !clarification.isBlank(); }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Step(String toolCode, JsonNode arguments) { }
}
