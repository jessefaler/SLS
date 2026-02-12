package com.protoxon.S4J.client.entities;

import com.protoxon.S4J.ServerStatus;
import org.json.JSONObject;

public final class StatusUpdateEvent extends ServerEvent {
    private final String serverId;
    private final ServerStatus status;

    public StatusUpdateEvent(String serverId, JSONObject payload) {
        super(serverId);
        this.serverId = serverId;
        this.status = ServerStatus.fromString(payload.optString("value"));
    }

    @Override
    public String getServerId() {
        return serverId;
    }

    public ServerStatus getStatus() {
        return status;
    }
}

