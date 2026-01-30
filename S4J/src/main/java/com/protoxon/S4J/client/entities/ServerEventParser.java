package com.protoxon.S4J.client.entities;

import com.protoxon.S4J.requests.WebSocketEvent;
import org.json.JSONObject;

public final class ServerEventParser {

    private ServerEventParser() {}

    public static ServerEvent parse(WebSocketEvent event) {
        JSONObject data = event.data;
        if (data == null) {
            return null;
        }

        String serverId = data.optString("server_id");
        if (serverId == null || serverId.isEmpty()) {
            return null;
        }

        JSONObject payload = data.optJSONObject("payload");
        if (payload == null) {
            Object rawPayload = data.opt("payload");
            payload = new JSONObject();
            if (rawPayload != null) {
                payload.put("value", rawPayload);
            }
        }

        switch (event.topic) {
            case "status":
                return new StatusUpdateEvent(serverId, payload);
            case "crash":
                return new ServerCrashEvent(serverId, payload);
            case "deleted":
                return new ServerDeletedEvent(serverId, payload);
            default:
                return null;
        }
    }
}

