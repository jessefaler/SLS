package com.protoxon.S4J.client.entities;

import org.json.JSONObject;

import java.time.Instant;

public final class ServerCrashEvent extends ServerEvent {
    private final String serverId;
    private final String reason;
    private final int exitCode;
    private final Instant timestamp;

    public ServerCrashEvent(String serverId, JSONObject payload) {
        super(serverId);
        this.serverId = serverId;
        this.reason = payload.optString("reason");
        this.exitCode = payload.optInt("exit_code");
        String timestampValue = payload.optString("timestamp");
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

