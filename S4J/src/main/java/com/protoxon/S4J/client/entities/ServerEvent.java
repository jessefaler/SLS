package com.protoxon.S4J.client.entities;

public sealed abstract class ServerEvent permits StatusUpdateEvent, ServerCrashEvent, ServerDeletedEvent {

    private final String serverId;

    protected ServerEvent(String serverId) {
        this.serverId = serverId;
    }

    public String getServerId() {
        return serverId;
    }
}


