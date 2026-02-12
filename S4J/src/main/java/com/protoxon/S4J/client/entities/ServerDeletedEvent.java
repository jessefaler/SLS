package com.protoxon.S4J.client.entities;

import org.json.JSONObject;

public final class ServerDeletedEvent extends ServerEvent {
    private final String serverId;

    public ServerDeletedEvent(String serverId, JSONObject payload) {
        super(serverId);
        this.serverId = serverId;
    }

    @Override
    public String getServerId() {
        return serverId;
    }
}

