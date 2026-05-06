package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.Allocation;
import com.protoxon.S4J.client.entities.ClientServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.actions.JoinActions;
import net.slimelabs.vsls.server.events.ServerEvents;
import net.slimelabs.vsls.utils.ViaVersion;
import net.slimelabs.vsls.utils.loader.Animation;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Server {

    private final String id;
    private final String shortId;
    private String name;
    private String compositeId;

    // The servers api client
    private final ClientServer client;
    // Runnable that removes this server from the registry when executed
    private final Runnable unregister;
    // The software version the server is using
    private volatile String version;
    // Servers last updated status
    private volatile ServerStatus status = ServerStatus.UNKNOWN;
    // Server events
    private final ServerEvents events = new ServerEvents();
    // Arbitrary server data
    private final ServerData serverData = new ServerData();
    // Lifecycle management, if false the LifecycleManager will not manage this instance
    private volatile boolean lifecycleEnabled = true;

    public Server(String name, String idPrefix, ClientServer client, Runnable unregister) {
        this.name = name;
        this.client = client;
        this.id = client.getId();
        this.shortId = id.length() >= 6 ? id.substring(0, 6) : id;
        this.compositeId = idPrefix + "." + shortId;
        this.unregister = unregister;
        new JoinActions(this);
    }

    /**
     * Returns the servers data object which is used to get or
     * store arbitrary key value data on a server instance
     * @return ServerData
     */
    public ServerData getServerData() {
        return serverData;
    }

    /**
     * Returns the servers current status
     * one of (Offline, Starting, Running, Stopping)
     * @return the status of the server
     */
    public ServerStatus getStatus() {
        return status;
    }

    public void setLifecycleEnabled(boolean value) {
        lifecycleEnabled = value;
    }

    public boolean isLifecycleEnabled() {
        return lifecycleEnabled;
    }

    /**
     * Returns the server's event manager, which allows subscribing
     * to server-related events such as status changes, crashes, and deletions.
     *
     * @return the {@link ServerEvents} instance for this server
     */
    public ServerEvents getEvents() {
        return events;
    }

    /**
     * Sets the servers status
     * @param status the status to set
     */
    public void setStatus(ServerStatus status) {
        this.status = status;
    }

    /**
     * Returns the full length id of the server
     * @return the servers id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns a composite identifier for this server in the format:
     * {@code <blueprintId>.<shortId>} (e.g., {@code example.zds89d}).
     * <p>
     * This identifier is human-readable and uniquely identifies a server
     * within the scope of its blueprint. It is useful for logging, display,
     * routing, and referencing servers in a concise, namespaced form.
     *
     * @return the composite (namespaced) server identifier
     */
    public String getCompositeId() {
        return compositeId;
    }

    /**
     * Returns the short server id: the first six characters of the API id (or the full id if shorter).
     * This is the suffix in {@link #getCompositeId()} after the blueprint id and separator.
     *
     * @return the shortened id
     */
    public String getShortId() {
        return shortId;
    }

    /**
     * Returns the name of this server
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the software version the server is using
     * @return the software version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the servers software version
     * @param version the version to set
     */
    protected void setVersion(String version) {
        this.version = version;
    }

    /**
     * Returns the blueprint id the server is from
     * @return the blueprint id
     */
    public String getBlueprintId() {
        return client.getBlueprintId();
    }

    public SLSAction<Void> stop() {
        return client.stop();
    }

    public SLSAction<Void> start() {
        return client.start();
    }

    public SLSAction<Void> pause() {
        return client.pause();
    }

    public SLSAction<Void> unpause() {
        return client.unpause();
    }

    public SLSAction<Void> restart() {
        return client.restart();
    }

    public SLSAction<Void> kill() {
        return client.kill();
    }

    public SLSAction<Void> reset() {
        return client.reset();
    }

    public SLSAction<Void> delete() {
        return client.delete();
    }

    /**
     * Deletes the server.
     * <p>
     * If {@code force} is {@code true}, the server will be removed from Protocube
     * even if deletion fails on the underlying node.
     *
     * @param force whether to force deletion when node-level deletion fails
     * @return an {@link SLSAction} callback that performs the deletion when executed
     */
    public SLSAction<Void> delete(boolean force) {
        return client.delete(force);
    }

    /**
     * Returns the servers ip and port allocation
     */
    public Allocation getAllocation() {
        return client.getAllocation();
    }

    /**
     * Fetches the servers current resource usage stats
     */
    public SLSAction<ServerStats> getStats() {
        return client.getStats();
    }

    /**
     * Fetches the servers current resource usage stats
     * @param update updates the cached disk usage
     */
    public SLSAction<ServerStats> getStats(boolean update) {
        return client.getStats(update);
    }

    /**
     * Removes the server from the manager when executed
     */
    public void unregister() {
        unregister.run();
    }

    /**
     * Executes a command on the server console.
     * @param command the command to execute
     */
    public SLSAction<Void> sendCommand(String command) {
        return client.sendCommand(command);
    }

    /**
     * Executes a list of commands on the server console.
     * @param commands the commands to execute
     */
    public SLSAction<Void> sendCommands(List<String> commands) {
        return client.sendCommands(commands);
    }

    /**
     * Returns the number of players currently connected to this server
     * as reported by the proxy.
     */
    public int getPlayerCount() {
        return SLS.proxy.getServer(getCompositeId())
                .map(rs -> rs.getPlayersConnected().size())
                .orElse(0);
    }

    /**
     * Returns the most recent 100 log lines from the server.
     *
     * @return an action that resolves to a list of log lines
     */
    public SLSAction<List<String>> getLogs() {
        return client.getLogs();
    }

    /**
     * Returns the specified number of most recent log lines from the server.
     *
     * @param lines the number of log lines to retrieve
     * @return an action that resolves to a list of log lines
     */
    public SLSAction<List<String>> getLogs(int lines) {
        return client.getLogs(lines);
    }

    /**
     * Fetches the servers status from the remote node
     */
    public SLSAction<ServerStatus> getRemoteStatus() {
        return client.getStatus();
    }

    // Returns the name of the node this server resides on
    public String getNodeName() {
        return client.getNodeName();
    }

    // Returns the id of the node this server resides on
    public String getNodeId() {
        return client.getNodeId();
    }

    /**
     * Connects a player to this server
     * @param player the player to connect
     */
    public void connect(Player player) {
        SLS.proxy.getServer(getCompositeId()).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).connectWithIndication().thenAccept(connection -> {
                }).exceptionally(throwable -> {
                    // Handle connection failure
                    Animation.clearSwitching(player.getUniqueId());
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Error: Could not connect to " + getCompositeId(), NamedTextColor.RED)
                            .sendMessage(player);
                    Log.withField("reason", throwable.getMessage()).error("Failed to connect {} to {}", player.getUsername(), getCompositeId());
                    return null;
                }),
                () -> {
                    Animation.clearSwitching(player.getUniqueId());
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Error: Server not registered with velocity", NamedTextColor.RED)
                            .sendMessage(player);
                }
        );
    }

    /**
     * Returns a comma-separated string of the usernames of players currently connected to this server.
     * This will never return null; if no players are connected, it returns an empty string.
     *
     * @return a comma-separated list of player usernames
     */
    public String getPlayerNames() {
        return SLS.proxy.getServer(getCompositeId())
                .map(rs -> rs.getPlayersConnected().stream()
                        .map(Player::getUsername)
                        .collect(Collectors.joining(", ")))
                .orElse("");
    }

    /**
     * Returns a list of Player objects currently connected to the specified server.
     * If the server is not found or no players are connected, returns an empty list.
     *
     * @return a list of players
     */
    public ArrayList<Player> getPlayers() {
        return SLS.proxy.getServer(getCompositeId())
                .map(rs -> new ArrayList<>(rs.getPlayersConnected()))
                .orElseGet(ArrayList::new);
    }

    /**
     * Sets the composite id prefix for the server
     * and reregisters the server in Velocity and ViaVersion
     * @param prefix the prefix to use
     */
    public void setCompositeIdPrefix(String prefix) {
        // Reregister the server with the new composite id
        SLS.proxy.getServer(getCompositeId()).ifPresent(registeredServer -> SLS.proxy.unregisterServer(registeredServer.getServerInfo()));
        ViaVersion.unregister(getId());
        this.compositeId = prefix + "." + shortId;
        InetSocketAddress address = new InetSocketAddress(
                getAllocation().getAlias().isEmpty() ? getAllocation().getIp() : getAllocation().getAlias(),
                getAllocation().getPort()
        );
        ServerInfo serverInfo = new ServerInfo(getCompositeId(), address);
        SLS.proxy.registerServer(serverInfo);
        ViaVersion.register(this);
    }

    /**
     * Sets the servers name
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

}
