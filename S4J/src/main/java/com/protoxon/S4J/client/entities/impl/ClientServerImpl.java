package com.protoxon.S4J.client.entities.impl;

import com.protoxon.S4J.PowerAction;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.Allocation;
import com.protoxon.S4J.client.entities.ClientServer;
import com.protoxon.S4J.client.entities.ServerLimits;
import com.protoxon.S4J.client.entities.ServerOverrides;
import com.protoxon.S4J.requests.PaginationAction;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.SLSActionImpl;
import com.protoxon.S4J.requests.action.operator.impl.StringPaginationResponseImpl;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ClientServerImpl implements ClientServer {

    private final JSONObject json;
    private final SLSClientImpl impl;
    private final Allocation allocation;
    private final ServerOverrides overrides;
    private final ServerLimits limits;

    public ClientServerImpl(JSONObject json, SLSClientImpl impl) {
        this.json = json;
        this.impl = impl;
        JSONObject allocationsObj = json.optJSONObject("allocations");
        this.allocation = allocationsObj != null ? new AllocationImpl(allocationsObj) : null;
        this.overrides = ServerOverrides.fromJson(json.optJSONObject("overrides"));
        this.limits = ServerLimits.fromJson(json.optJSONObject("limits"));
    }

    @Override
    public String getId() {
        return json.getString("id");
    }

    @Override
    public String getBlueprintId() {
        return json.getString("blueprint_id");
    }

    @Override
    public String getNodeId() {
        return json.getString("node_id");
    }

    @Override
    public String getNodeName() {
        return json.getString("node_name");
    }

    @Override
    public Allocation getAllocation() {
        return allocation;
    }

    @Override
    public ServerOverrides getOverrides() {
        return overrides;
    }

    @Override
    public String getSoftwareId() {
        return json.optString("software_id", null);
    }

    @Override
    public String getSoftwareVersion() {
        return json.optString("software_version", null);
    }

    @Override
    public String getImage() {
        return json.optString("image", null);
    }

    @Override
    public ServerLimits getLimits() {
        return limits;
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
    public SLSAction<ServerStats> getStats(boolean update) {
        Route.CompiledRoute route = Route.Server.STATS.compile(getId());
        if (update) {
            route = route.withQueryParams("update_disk_usage", "true");
        }
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(),
                route,
                (response, request) -> ServerStats.fromJSON(response.getObject()));
    }

    @Override
    public String getIp() {
        return allocation != null ? allocation.getIp() : null;
    }

    @Override
    public int getPort() {
        return allocation != null ? allocation.getPort() : 0;
    }

    @Override
    public SLSAction<Void> sendCommand(String command) {
        return sendCommands(command);
    }

    @Override
    public SLSAction<Void> sendCommands(String... commands) {
        JSONObject obj = new JSONObject();
        JSONArray commandsArray = new JSONArray();
        for (String command : commands) {
            commandsArray.put(command);
        }
        obj.put("commands", commandsArray);
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(), Route.Server.COMMANDS.compile(getId()), SLSActionImpl.getRequestBody(obj));
    }

    @Override
    public SLSAction<Void> sendCommands(List<String> commands) {
        JSONObject obj = new JSONObject();
        JSONArray commandsArray = new JSONArray();
        for (String command : commands) {
            commandsArray.put(command);
        }
        obj.put("commands", commandsArray);
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(), Route.Server.COMMANDS.compile(getId()), SLSActionImpl.getRequestBody(obj));
    }

    @Override
    public PaginationAction<String> getLogs() {
        return StringPaginationResponseImpl.onPagination(
                impl.getS4J(), Route.Server.LOGS.compile(getId()));
    }

    @Override
    public PaginationAction<String> getInstallLogs() {
        return StringPaginationResponseImpl.onPagination(
                impl.getS4J(),
                Route.Server.LOGS.compile(getId()).withQueryParams("type", "install"));
    }

    @Override
    public SLSAction<Void> delete() {
        return delete(false);
    }

    @Override
    public SLSAction<Void> delete(boolean force) {
        Route.CompiledRoute route = Route.Server.DELETE.compile(getId());
        if (force) {
            route = route.withQueryParams("force", "true");
        }
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(), route);
    }

    @Override
    public SLSAction<Void> reset() {
        return SLSActionImpl.onRequestExecute(
                impl.getS4J(), Route.Server.RESET.compile(getId()));
    }

}
