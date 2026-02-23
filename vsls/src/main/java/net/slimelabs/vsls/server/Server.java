package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.Allocation;
import com.protoxon.S4J.client.entities.ClientServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.events.ServerEvents;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Server {

    private final String id;
    private final String shortId;
    private final String name;

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

    public Server(String name, ClientServer client, Runnable unregister) {
        this.name = name;
        this.client = client;
        this.id = client.getId();
        this.shortId = id.length() >= 6 ? id.substring(0, 6) : id;
        this.unregister = unregister;
    }

    /**
     * Returns the servers current status
     * one of (Offline, Starting, Running, Stopping)
     * @return the status of the server
     */
    public ServerStatus getStatus() {
        return status;
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
     * Returns a shortened version of the servers id
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
     * Returns the number of players currently connected to this server
     * as reported by the proxy.
     */
    public int getPlayerCount() {
        return SLS.proxy.getServer(getShortId())
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
        SLS.proxy.getServer(getShortId()).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).connectWithIndication().thenAccept(connection -> {
                }).exceptionally(throwable -> {
                    // Handle connection failure
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Error: Could not connect to " + getShortId(), NamedTextColor.RED)
                            .sendMessage(player);
                    Log.withField("reason", throwable.getMessage()).error("Failed to connect {} to {}", player.getUsername(), getShortId());
                    return null;
                }),
                () -> ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("Error: Server not registered with velocity", NamedTextColor.RED)
                        .sendMessage(player)
        );
    }

    /**
     * Returns a comma-separated string of the usernames of players currently connected to this server.
     * This will never return null; if no players are connected, it returns an empty string.
     *
     * @return a comma-separated list of player usernames
     */
    public String getPlayerNames() {
        return SLS.proxy.getServer(getShortId())
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
        return SLS.proxy.getServer(getShortId())
                .map(rs -> new ArrayList<>(rs.getPlayersConnected()))
                .orElseGet(ArrayList::new);
    }

}
