package com.protoxon.S4J.client.entites.impl;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entites.SLSClient;
import com.protoxon.S4J.client.entites.WebSocketEventStream;
import com.protoxon.S4J.entites.Blueprint;
import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.entites.impl.BlueprintImpl;
import com.protoxon.S4J.requests.PaginationAction;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.SLSActionImpl;
import com.protoxon.S4J.requests.action.operator.impl.PaginationResponseImpl;

import java.util.stream.Stream;

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
}
