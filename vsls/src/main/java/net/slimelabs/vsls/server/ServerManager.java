package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entities.ClientServer;
import com.protoxon.S4J.client.entities.SLSClient;
import com.protoxon.S4J.entities.Blueprint;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.events.EventRouter;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.ViaVersion;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerManager implements ServerProvider {

    ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();
    private final ServerEventRouter router;
    private final SLSClient api;

    public ServerManager(SLSClient api, EventRouter router) {
        this.api = api;
        // Initialize the event router
        this.router = new ServerEventRouter(router, this);
        // Load servers from the api
        loadServers(this, api);
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
                    registry.loadServer(clientServer);
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

    public Server loadServer(ClientServer clientServer) {
        Blueprint blueprint = SLS.blueprints.getBlueprint(clientServer.getBlueprintId());
        Server server;
        if (blueprint != null) {
            server = new Server(
                    blueprint.getName(),
                    clientServer,
                    () -> unRegister(clientServer.getId())
            );
        } else {
            // Blueprint not found, register it with the clientServers provided blueprint id
            server = new Server(clientServer.getBlueprintId(), clientServer, () -> unRegister(clientServer.getId()));
            Log.warn("Blueprint not found for server {} with blueprint id: {}", clientServer.getId(), clientServer.getBlueprintId());
        }
        // Set the version from the creation action or from the blueprint if not set
        var overrides = clientServer.getOverrides();
        String versionOverride = overrides != null ? overrides.getVersion() : null;
        server.setVersion(
                versionOverride != null && !versionOverride.isEmpty()
                        ? versionOverride
                        : (blueprint != null ? blueprint.getServerVersion() : "null")
        );
        register(server);
        // Fetch the servers status and update it locally
        clientServer.getStatus().executeAsync(server::setStatus);
        return server;
    }

    public Server getServer(String id) {
        return servers.get(id);
    }

    /**
     * Attempts to get the server from the manager or fetch from the API if not present.
     * Returns an empty Optional if the server cannot be found.
     */
    public Optional<Server> getOrFetch(String id) {
        Server server = getServer(id);
        if (server != null) return Optional.of(server);
        // The server is not in the manager try to fetch it
        ClientServer clientServer = api.getServer(id).execute();
        return Optional.ofNullable(SLS.servers.loadServer(clientServer));
    }

    /**
     * Finds the first server whose ID starts with the provided prefix.
     *
     * @param id the ID prefix to search for
     * @return the first matching Server, or null if no server matches the prefix
     */
    public Server resolve(String id) {
        for (Server server : servers.values()) {
            if (server.getId().startsWith(id)) return server;
        }
        return null;
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
            Server server = new Server(name, clientServer, () -> unRegister(clientServer.getId()));
            // Set the version from the creation action or from the blueprint if not set
            server.setVersion(!Objects.equals(action.getVersion(), "")
                    ? action.getVersion()
                    : (blueprint != null ? blueprint.getServerVersion() : "null"));
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
        servers.put(server.getId(), server);
        // Register the server with velocity
        // Create the socket address
        // Use the alias as the address if present
        InetSocketAddress address = new InetSocketAddress(
                server.getAllocation().getAlias().isEmpty() ? server.getAllocation().getIp() : server.getAllocation().getAlias(),
                server.getAllocation().getPort()
        );
        ServerInfo serverInfo = new ServerInfo(server.getShortId(), address);
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
     * Gets the short id's of all servers in the registry.
     * @return a collection of all server short id's
     */
    public Collection<String> getShortIds() {
        return servers.values().stream()
                .map(Server::getShortId)
                .toList();
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
            server.getEvents().clearAllListeners();
            // Unregister the server in velocity
            SLS.proxy.getServer(server.getShortId()).ifPresent(registeredServer -> SLS.proxy.unregisterServer(registeredServer.getServerInfo()));
            ViaVersion.unregister(id);
        }
    }

}

