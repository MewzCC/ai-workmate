package com.aiworkmate.agent.gateway;

public record WorkerLease(String workerId, int attempt, String leaseToken) {
    public WorkerLease {
        if (workerId == null || workerId.isBlank() || workerId.length() > 80) {
            throw new IllegalArgumentException("Invalid worker lease");
        }
        if (attempt < 0 || attempt > 2 || leaseToken == null || leaseToken.length() < 32 || leaseToken.length() > 128) {
            throw new IllegalArgumentException("Invalid worker lease");
        }
    }

    @Override
    public String toString() {
        return "WorkerLease[workerId=" + workerId + ", attempt=" + attempt + ", leaseToken=[REDACTED]]";
    }
}
