package com.protoxon.S4J.client.entities;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.entities.Blueprint;
import com.protoxon.S4J.entities.SystemInformation;
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

    /**
     * Retrieves a single blueprint by its ID
     * @param id The blueprint ID
     * @return SLSAction that returns the blueprint with the given ID
     */
    SLSAction<Blueprint> getBlueprint(String id);

    WebSocketEventStream getEventStream();

    SLSAction<Void> reloadBlueprints();
    SLSAction<Void> reloadSoftwareConfigs();

    /**
     * Retrieves system information from the protocube API
     * @return SLSAction that returns system information
     */
    SLSAction<SystemInformation> getSystemInformation();

    /**
     * Retrieves all nodes
     * @return SLSAction that returns a list of all nodes
     */
    SLSAction<List<ClientNode>> getAllNodes();

    /**
     * Retrieves only the IDs of all nodes
     * @return SLSAction that returns a list of node IDs
     */
    SLSAction<List<String>> getAllNodeIds();

    /**
     * Retrieves a single node by its ID
     * @param id The node ID
     * @return SLSAction that returns the node with the given ID
     */
    SLSAction<ClientNode> getNode(String id);

}
