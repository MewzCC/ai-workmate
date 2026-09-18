package com.aiworkmate.service.model;

/** Framework-neutral, version-bound command for one actual seal-use registration. */
public record SealAgentUseCommand(long usageId, int expectedVersion, int actualCopies, String remark) {
}
