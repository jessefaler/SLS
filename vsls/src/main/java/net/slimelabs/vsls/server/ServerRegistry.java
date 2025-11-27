package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.SLSClient;
import com.protoxon.S4J.entites.Blueprint;
import com.protoxon.S4J.entites.S4J;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ServerRegistry implements ServerProvider {

    HashMap<String, Server> servers = new HashMap<>();
    private SLSClient api;
    private Events events;

    public ServerRegistry(SLSClient api) {
        this.api = api;
        // Initialize the event listener
        events = Events.init(api, this);
    }

    /**
     * Initializes the server registry
     * @param api the S4J api client
     * @return the initialized registry
     */
    public static ServerRegistry init(SLSClient api) {
        return new ServerRegistry(api);
    }

    public Server getServer(String id) {
        return servers.get(id);
    }

    /**
     * Initiates the creation of a new server using the specified blueprint id.
     *
     * @param blueprint the ID of the blueprint to base the server on
     * @return an SLSAction that, when executed, creates the server and registers it
     */
    public SLSAction<Server> CreateServer(String blueprint) {
        SLSAction<ClientServer> action = api.createServer().setBlueprintId(blueprint);
        // Map the ClientServer to a vSLS Server, and register it
        return action.map(clientServer -> {
            Server server = new Server(clientServer, () -> unRegister(clientServer.getId()));
            // Add the server to the registry using the client server's ID
            servers.put(clientServer.getId(), server);
            return server;
        });
    }

    /**
     * Gets the id's of all servers in the registry
     * @return Collection of all server id's
     */
    public Collection<String> getIds() {
        return servers.keySet();
    }

    /**
     * Gets all servers in the registry
     * @return collection of all the servers
     */
    public Collection<Server> getAll() {
        return servers.values();
    }

    /**
     * Unregisters the server with the given id
     * @param id the servers id
     */
    public void unRegister(String id) {
        Server server = servers.get(id);
        server.onUnregistration();
        servers.remove(id);
        Log.debug("Server " + id + " unregistered");
    }

    /**
     * Returns the event listener
     * @return An event listener
     */
    public Events getEvents() {
        return events;
    }


}

