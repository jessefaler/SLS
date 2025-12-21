package com.protoxon.S4J.client.entites.impl;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.SLSClient;
import com.protoxon.S4J.client.entites.WebSocketEventStream;
import com.protoxon.S4J.entites.Blueprint;
import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.entites.SystemInformation;
import com.protoxon.S4J.entites.impl.BlueprintImpl;
import com.protoxon.S4J.entites.impl.SystemInformationImpl;
import com.protoxon.S4J.requests.PaginationAction;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.SLSActionImpl;
import com.protoxon.S4J.requests.action.operator.impl.PaginationResponseImpl;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SLSClientImpl implements SLSClient {

    private final S4J api;

    public SLSClientImpl(S4J api) {
        this.api = api;
    }

    public S4J getS4J() {
        return api;
    }

    @Override
    public ServerCreationAction createServer() {
        return new CreateServerImpl(this);
    }

    @Override
    public SLSAction<List<ClientServer>> getAllServers() {
        return SLSActionImpl.onRequestExecute(
                api,
                Route.Servers.GET_ALL_SERVERS.compile(),
                (response, request) -> {
                    JSONArray array = response.getArray();
                    List<ClientServer> servers = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject serverObj = array.getJSONObject(i);
                        servers.add(new ClientServerImpl(serverObj, this));
                    }
                    return servers;
                });
    }

    @Override
    public SLSAction<List<String>> getAllServerIds() {
        return SLSActionImpl.onRequestExecute(
                api,
                Route.Servers.GET_ALL_SERVERS.compile().withQueryParams("ids_only", "true"),
                (response, request) -> {
                    JSONArray array = response.getArray();
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        ids.add(array.getString(i));
                    }
                    return ids;
                });
    }

    @Override
    public SLSAction<ClientServer> getServer(String id) {
        return SLSActionImpl.onRequestExecute(
                api,
                Route.Server.GET_SERVER.compile(id),
                (response, request) -> {
                    JSONObject serverObj = response.getObject();
                    return new ClientServerImpl(serverObj, this);
                });
    }

    @Override
    public PaginationAction<Blueprint> getBlueprints() {
        return PaginationResponseImpl.onPagination(
                api, Route.Blueprints.GET_BLUEPRINTS.compile(), (object) -> new BlueprintImpl(object, this));
    }

    @Override
    public WebSocketEventStream getEventStream() {
        return new WebSocketEventStreamImpl(api);
    }

    @Override
    public SLSAction<Void> reloadBlueprints() {
        return SLSActionImpl.onRequestExecute(api, Route.Blueprints.RELOAD.compile());
    }

    @Override
    public SLSAction<Void> reloadSoftwareConfigs() {
        return SLSActionImpl.onRequestExecute(api, Route.Software.RELOAD.compile());
    }

    @Override
    public SLSAction<SystemInformation> getSystemInformation() {
        return SLSActionImpl.onRequestExecute(
                api,
                Route.System.GET_SYSTEM_INFORMATION.compile(),
                (response, request) -> {
                    JSONObject systemObj = response.getObject();
                    return new SystemInformationImpl(systemObj);
                });
    }
}
