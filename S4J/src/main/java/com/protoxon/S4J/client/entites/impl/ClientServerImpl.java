package com.protoxon.S4J.client.entites.impl;

import com.protoxon.S4J.PowerAction;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.SLSActionImpl;
import org.json.JSONObject;

public class ClientServerImpl implements ClientServer {

    private final JSONObject json;
    private final SLSClientImpl impl;

    public ClientServerImpl(JSONObject json, SLSClientImpl impl) {
        this.json = json;
        this.impl = impl;
    }

    @Override
    public String getId() {
        return json.getString("id");
    }

    @Override
    public SLSAction<Void> setPower(PowerAction powerAction) {
        JSONObject obj = new JSONObject().put("action", powerAction.name().toLowerCase());
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(), Route.Server.SET_POWER.compile(getId()), SLSActionImpl.getRequestBody(obj));
    }

    public SLSAction<ServerStatus> getStatus() {
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(),
                Route.Server.STATUS.compile(getId()),
                (response, request) -> {
                    String statusString = response.getObject().optString("status", "unknown");
                    return ServerStatus.fromString(statusString);
                });
    }

    @Override
    public SLSAction<ServerStats> getStats() {
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(),
                Route.Server.STATS.compile(getId()),
                (response, request) -> ServerStats.fromJSON(response.getObject()));
    }

    @Override
    public String getIp() {
        return json.getString("ip");
    }

    @Override
    public int getPort() {
        return json.getInt("port");
    }

}
