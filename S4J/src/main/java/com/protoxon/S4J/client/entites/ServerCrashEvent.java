package com.protoxon.S4J.client.entites;

import org.json.JSONObject;

import java.time.Instant;

public class ServerCrashEvent implements ServerEvent {
    private final String serverId;
    private final String reason;
    private final int exitCode;
    private final Instant timestamp;

    public ServerCrashEvent(String serverId, JSONObject payload) {
        this.serverId = serverId;
        this.reason = payload.optString("Reason");
        this.exitCode = payload.optInt("ExitCode");
        String timestampValue = payload.optString("Timestamp");
        this.timestamp = timestampValue.isEmpty() ? Instant.now() : Instant.parse(timestampValue);
    }

    @Override
    public String getServerId() {
        return serverId;
    }

    public String getReason() {
        return reason;
    }

    public int getExitCode() {
        return exitCode;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}

