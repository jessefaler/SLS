package com.protoxon.S4J;

import org.json.JSONObject;

import java.time.Instant;

/**
 * Represents the current installation state for a server instance.
 */
public class InstallInfo {
    private final InstallPhase phase;
    private final String containerId;
    private final String containerName;
    private final String status;
    private final Long exitCode;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final String failureReason;

    public InstallInfo(
            InstallPhase phase,
            String containerId,
            String containerName,
            String status,
            Long exitCode,
            Instant startedAt,
            Instant finishedAt,
            String failureReason) {
        this.phase = phase;
        this.containerId = containerId;
        this.containerName = containerName;
        this.status = status;
        this.exitCode = exitCode;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.failureReason = failureReason;
    }

    public static InstallInfo fromJSON(JSONObject json) {
        InstallPhase phase = InstallPhase.fromString(json.optString("phase", "unknown"));
        String containerId = json.optString("container_id", null);
        String containerName = json.optString("container_name", null);
        String status = json.optString("status", null);
        Long exitCode = json.has("exit_code") && !json.isNull("exit_code")
                ? json.getLong("exit_code")
                : null;
        Instant startedAt = parseInstant(json.optString("started_at", null));
        Instant finishedAt = parseInstant(json.optString("finished_at", null));
        String failureReason = json.optString("failure_reason", null);

        return new InstallInfo(
                phase,
                containerId,
                containerName,
                status,
                exitCode,
                startedAt,
                finishedAt,
                failureReason);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    public InstallPhase getPhase() {
        return phase;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getStatus() {
        return status;
    }

    public Long getExitCode() {
        return exitCode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
