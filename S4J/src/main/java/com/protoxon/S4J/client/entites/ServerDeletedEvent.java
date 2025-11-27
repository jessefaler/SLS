package com.protoxon.S4J.client.entites;

import org.json.JSONObject;

public class ServerDeletedEvent implements ServerEvent {
    private final String serverId;

    public ServerDeletedEvent(String serverId, JSONObject payload) {
        this.serverId = serverId;
    }

    @Override
    public String getServerId() {
        return serverId;
    }
}

