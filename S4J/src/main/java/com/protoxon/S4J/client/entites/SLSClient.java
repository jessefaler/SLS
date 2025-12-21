package com.protoxon.S4J.client.entites;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.entites.Blueprint;
import com.protoxon.S4J.entites.SystemInformation;
import com.protoxon.S4J.requests.PaginationAction;

import java.util.List;

public interface SLSClient {

    ServerCreationAction createServer();

    /**
     * Retrieves all servers
     * @return SLSAction that returns a list of all servers
     */
    SLSAction<List<ClientServer>> getAllServers();

    /**
     * Retrieves only the IDs of all servers
     * @return SLSAction that returns a list of server IDs
     */
    SLSAction<List<String>> getAllServerIds();

    /**
     * Retrieves a single server by its ID
     * @param id The server ID
     * @return SLSAction that returns the server with the given ID
     */
    SLSAction<ClientServer> getServer(String id);

    PaginationAction<Blueprint> getBlueprints();

    WebSocketEventStream getEventStream();

    SLSAction<Void> reloadBlueprints();
    SLSAction<Void> reloadSoftwareConfigs();

    /**
     * Retrieves system information from the protocube API
     * @return SLSAction that returns system information
     */
    SLSAction<SystemInformation> getSystemInformation();

}
