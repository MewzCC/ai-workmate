package com.aiworkmate.service.model;

/** Framework-neutral, version-bound command for one visitor lifecycle transition. */
public record VisitorAgentVisitCommand(long bookingId, int expectedVersion, String remark) {
}
