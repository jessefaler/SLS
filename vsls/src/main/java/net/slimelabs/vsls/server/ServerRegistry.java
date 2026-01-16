package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.SLSClient;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.ViaVersion;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ServerRegistry implements ServerProvider {

    ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();
    private SLSClient api;
    public Events events;

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
     * Initiates the creation of a new server
     *
     * @param action the server creation action
     * @return an SLSAction that, when executed, creates the server and registers it
     */
    public SLSAction<Server> CreateServer(ServerCreationAction action) {
        // Map the ClientServer to a vSLS Server, and register it
        return action.map(clientServer -> {
            String name = SLS.blueprints.getBlueprint(action.getBlueprintId()).getName();
            Server server = new Server(name, clientServer, action.getBlueprintId(), () -> unRegister(clientServer.getId()));
            register(server);
            return server;
        });
    }

    /**
     * Adds the server to the server registry
     * and registers the server with velocity
     * and registers the server with viaversion
     * @param server the server to register
     */
    public void register(Server server) {
        servers.put(server.id, server);
        // Register the server with velocity
        InetSocketAddress address = new InetSocketAddress(server.getIp(), server.getPort()); // Create socket address
        ServerInfo serverInfo = new ServerInfo(server.id, address);
        SLS.proxy.registerServer(serverInfo);
        // Register the server with ViaVersion
        ViaVersion.register(server);
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
        servers.remove(id);
        if(server != null) {
            server.handleUnregistration();
            server.clearListeners();
        }
        // Unregister the server in velocity
        SLS.proxy.getServer(id).ifPresent(registeredServer -> SLS.proxy.unregisterServer(registeredServer.getServerInfo()));
        ViaVersion.unregister(id);
        Log.debug("Server " + id + " unregistered");
    }

    /**
     * Returns the event listener
     * @return The event listener
     */
    public Events getEvents() {
        return events;
    }


}

