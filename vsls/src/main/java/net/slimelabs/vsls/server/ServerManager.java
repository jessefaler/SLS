package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entites.Allocation;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.SLSClient;
import com.protoxon.S4J.entites.Blueprint;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.ViaVersion;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerManager implements ServerProvider {

    ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();
    private SLSClient api;
    public Events events;

    public ServerManager(SLSClient api) {
        this.api = api;
        // Initialize the event listener
        events = Events.init(api, this);
    }

    /**
     * Initializes the server registry.
     * <p>
     * Fetches all servers asynchronously from the API and populates the registry.
     * If the fetch fails, it will retry every 30 seconds until successful.
     * Any errors encountered during the fetch are logged.
     *
     * @param api the S4J api client
     * @return the initialized registry
     */
    public static ServerManager init(SLSClient api) {
        ServerManager registry = new ServerManager(api);
        loadServers(registry, api);
        return registry;
    }

    /**
     * Attempts to load servers from the API. If it fails, schedules a retry after 30 seconds.
     * This will continue retrying until successful.
     *
     * @param registry the registry instance to populate
     * @param api the S4J api client
     */
    private static void loadServers(ServerManager registry, SLSClient api) {
        api.getAllServers().executeAsync(servers -> {
            SLS.blueprints.whenLoaded(blueprints -> {
                for (ClientServer clientServer : servers) {
                    Blueprint blueprint = blueprints.getBlueprint(clientServer.getBlueprintId());
                    Server server;
                    if (blueprint != null) {
                        server = new Server(
                                blueprint.getName(),
                                clientServer,
                                blueprint.getId() != null ? blueprint.getId() : "Unknown",
                                () -> registry.unRegister(clientServer.getId())
                        );
                        registry.register(server);
                    } else {
                        // Blueprint not found, register with default values
                        server = new Server("Unknown", clientServer, "Unknown", () -> registry.unRegister(clientServer.getId()));
                        registry.register(server);
                        Log.warn("Blueprint not found for server {} with blueprint ID: {}", clientServer.getId(), clientServer.getBlueprintId());
                    }
                    // Fetch the servers status and update it locally
                    clientServer.getStatus().executeAsync(status -> {
                        server.status = status;
                    });
                }
                Log.info("Initialized server registry. Loaded {} servers", servers.size());
            });
        }, failure -> {
            Log.warn("Failed to load servers: {}. Retrying in 30 seconds...", failure.getMessage());
            // Schedule a retry after 30 seconds
            SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
                loadServers(registry, api);
            }).delay(30, TimeUnit.SECONDS).schedule();
        });
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
    public SLSAction<Server> createServer(ServerCreationAction action) {
        // Map the ClientServer to a vSLS Server, and register it
        return action.map(clientServer -> {
            Blueprint blueprint = SLS.blueprints.getBlueprint(action.getBlueprintId());
            String name = Objects.requireNonNullElse(blueprint != null ? blueprint.getName() : null, action.getBlueprintId());
            Server server = new Server(name, clientServer, action.getBlueprintId(), () -> unRegister(clientServer.getId()));
            register(server);
            return server;
        });
    }

    /**
     * Registers a server with the manager, Velocity, and ViaVersion.
     *
     * @param server the server to register
     */
    public void register(Server server) {
        servers.put(server.id, server);
        // Register the server with velocity
        // Create the socket address
        // Use the alias as the address if present
        InetSocketAddress address = new InetSocketAddress(
                server.getAllocation().getAlias().isEmpty() ? server.getAllocation().getIp() : server.getAllocation().getAlias(),
                server.getAllocation().getPort()
        );
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
            server.clearListeners();
        }
        // Unregister the server in velocity
        SLS.proxy.getServer(id).ifPresent(registeredServer -> SLS.proxy.unregisterServer(registeredServer.getServerInfo()));
        ViaVersion.unregister(id);
    }

    /**
     * Returns the event listener
     * @return The event listener
     */
    public Events getEvents() {
        return events;
    }


}

