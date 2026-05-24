package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entities.ClientServer;
import com.protoxon.S4J.client.entities.SLSClient;
import com.protoxon.S4J.entities.Blueprint;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.blueprints.annotations.VslsAnnotations;
import net.slimelabs.vsls.events.EventRouter;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.events.ServerEventRouter;
import net.slimelabs.vsls.server.events.GlobalEvents;
import net.slimelabs.vsls.server.lifecycle.LifecycleManager;
import net.slimelabs.vsls.utils.VersionFetcher;
import net.slimelabs.vsls.utils.ViaVersion;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServerManager implements ServerProvider {

    ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();
    private final ServerEventRouter router;
    private final SLSClient api;
    private final GlobalEvents events = new GlobalEvents();
    private volatile boolean isLoaded = false;
    private final List<Consumer<ServerManager>> loadCallbacks = new CopyOnWriteArrayList<>();

    public ServerManager(SLSClient api, EventRouter router) {
        this.api = api;
        // Initialize the event router
        this.router = new ServerEventRouter(router, this, events);
        // Load servers from the api
        loadServers(this, api);
        // Start the lifecycle manager, if enabled
        if(SLS.config.lifecycle.enabled) {
            LifecycleManager lifecycleManager = new LifecycleManager(this);
            lifecycleManager.start();
        }
    }

    /**
     * Returns the global event listeners
     * <p>
     * Allow listing for events emitted by all servers
     */
    public GlobalEvents getEvents() {
        return events;
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
                registry.markLoaded();
            });
        }, failure -> {
            Log.warn("Failed to load servers: {}. Retrying in 30 seconds...", failure.info());
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
                    clientServer.getBlueprintId(),
                    clientServer,
                    () -> unRegister(clientServer.getId())
            );
        } else {
            // Blueprint not found, register it with the clientServers provided blueprint id
            server = new Server(clientServer.getBlueprintId(), clientServer.getBlueprintId(), clientServer, () -> unRegister(clientServer.getId()));
            Log.warn("Blueprint not found for server {} with blueprint id: {}", clientServer.getId(), clientServer.getBlueprintId());
        }
        register(server);
        VersionFetcher.resolveVersion(clientServer.getOverrides(), blueprint, api.getAllServers().getS4J())
                .executeAsync(
                        version -> server.setVersion(version != null ? version : "null"),
                        failure -> Log.warn("Failed to resolve version for server {}: {}", clientServer.getId(), failure.info())
                );
        // Fetch the servers status and update it locally
        clientServer.getStatus().executeAsync(server::setStatus);
        // Set weather to manage the servers lifecycle
        server.setLifecycleEnabled(!VslsAnnotations.dontStopWhenEmpty(blueprint));
        return server;
    }

    public Server getServer(String id) {
        return servers.get(id);
    }

    /**
     * Attempts to get the server from the manager or fetch from the API if not present.
     * Returns an empty Optional if the server cannot be found (e.g. 404 when the server
     * was already removed, such as after a failed start that triggered deletion).
     */
    public Optional<Server> getOrFetch(String id) {
        Server server = getServer(id);
        if (server != null) return Optional.of(server);
        try {
            ClientServer clientServer = api.getServer(id).execute();
            return Optional.ofNullable(SLS.servers.loadServer(clientServer));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Finds the first server that matches the given string as either the full API id,
     * a prefix of the API id, or a prefix of the composite id ({@link Server#getCompositeId()}).
     *
     * @param id the API id, composite id, or a prefix of either
     * @return the first matching Server, or null if no server matches
     */
    public Server resolve(String id) {
        Server exact = servers.get(id);
        if (exact != null) return exact;
        for (Server server : servers.values()) {
            if (server.getId().startsWith(id)) return server;
            if (server.getCompositeId().startsWith(id)) return server;
        }
        return null;
    }

    /**
     * Initiates the creation of a new server
     *
     * @param action the server creation action
     * @param name the name of the server
     * @param idPrefix the prefix to use for the composite id
     * @return an SLSAction that, when executed, creates the server and registers it
     */
    public SLSAction<Server> createServer(ServerCreationAction action, String name, String idPrefix) {
        // Map the ClientServer to a vSLS Server, and register it
        return action.map(clientServer -> {
            Blueprint blueprint = SLS.blueprints.getBlueprint(action.getBlueprintId());
            Server server = new Server(name, idPrefix, clientServer, () -> unRegister(clientServer.getId()));
            // Set the version from the creation action or from the blueprint if not set
            server.setVersion(!Objects.equals(action.getVersion(), "")
                    ? action.getVersion()
                    : (blueprint != null ? blueprint.getServerVersion() : "null"));
            // Set weather to manage the servers lifecycle
            server.setLifecycleEnabled(!VslsAnnotations.dontStopWhenEmpty(blueprint));
            register(server);
            return server;
        });
    }

    /**
     * Initiates the creation of a new server
     * <p>
     * Uses the blueprint's name as the server name and the blueprint's ID
     * as the composite id prefix, then delegates to
     * createServer(ServerCreationAction action, String name, String idPrefix).
     *
     * @param action the server creation action
     * @return an SLSAction that, when executed, creates the server and registers it
     */
    public SLSAction<Server> createServer(ServerCreationAction action) {
        Blueprint blueprint = SLS.blueprints.getBlueprint(action.getBlueprintId());
        String name = Objects.requireNonNullElse(
                blueprint != null ? blueprint.getName() : null,
                action.getBlueprintId());
        String idPrefix = blueprint != null ? blueprint.getId() : action.getBlueprintId();
        return createServer(action, name, idPrefix);
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
        ServerInfo serverInfo = new ServerInfo(server.getCompositeId(), address);
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
                .map(Server::getCompositeId)
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
            SLS.proxy.getServer(server.getCompositeId()).ifPresent(registeredServer -> SLS.proxy.unregisterServer(registeredServer.getServerInfo()));
            ViaVersion.unregister(id);
        }
    }

    /**
     * Checks if the server manager has completed its initial load.
     *
     * @return true if the manager has loaded servers at least once, false otherwise
     */
    public boolean isLoaded() {
        return isLoaded;
    }

    /**
     * Registers a callback to be executed when the server manager is loaded.
     * If the manager is already loaded, the callback will be executed immediately.
     * If the manager is not yet loaded, the callback will be executed once loading completes.
     *
     * @param callback the callback to execute when the manager is loaded, receives this ServerManager instance
     */
    public void whenLoaded(Consumer<ServerManager> callback) {
        if (isLoaded) {
            try {
                callback.accept(this);
            } catch (Exception e) {
                Log.error("Error executing server manager load callback", e);
            }
        } else {
            loadCallbacks.add(callback);
            // Double check in case we loaded while adding.
            if (isLoaded) {
                loadCallbacks.remove(callback);
                try {
                    callback.accept(this);
                } catch (Exception e) {
                    Log.error("Error executing server manager load callback", e);
                }
            }
        }
    }

    private void markLoaded() {
        isLoaded = true;
        for (Consumer<ServerManager> callback : loadCallbacks) {
            try {
                callback.accept(this);
            } catch (Exception e) {
                Log.error("Error executing server manager load callback", e);
            }
        }
        loadCallbacks.clear();
    }


}

