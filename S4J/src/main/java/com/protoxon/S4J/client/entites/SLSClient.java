package com.protoxon.S4J.client.entites;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.entites.Blueprint;
import com.protoxon.S4J.requests.PaginationAction;

public interface SLSClient {

    ServerCreationAction createServer();

    PaginationAction<Blueprint> getBlueprints();

    WebSocketEventStream getEventStream();

    SLSAction<Void> reloadBlueprints();
    SLSAction<Void> reloadSoftwareConfigs();

}
