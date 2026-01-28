package net.slimelabs.vsls.server;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.ServerCrashEvent;
import com.protoxon.S4J.entites.Blueprint;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class Server extends Listener {

    public String id;
    public String name;
    // The servers api client
    public ClientServer client;
    // The id of the blueprint this server was created from
    public String blueprintId;
    // Runnable that removes this server from the registry when executed
    private final Runnable unregister;
    // Servers last updated status
    public ServerStatus status = ServerStatus.UNKNOWN;

    public Server(String name, ClientServer client, String blueprintId, Runnable unregister) {
        this.name = name;
        this.client = client;
        this.blueprintId = blueprintId;
        this.id = client.getId();
        this.unregister = unregister;
    }

    public String getId() {
        return id;
    }

    public SLSAction<Void> stop() {
        return client.stop();
    }

    public SLSAction<Void> start() {
        return client.start();
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

    public int getPort() {
        return client.getPort();
    }

    public String getIp() {
        return client.getIp();
    }

    public SLSAction<ServerStats> getStats() {
        return client.getStats();
    }

    public SLSAction<ServerStats> getStats(boolean update) {
        return client.getStats(update);
    }

    public void unregister() {
        unregister.run();
    }

    public SLSAction<Void> sendCommand(String command) {
        return client.sendCommand(command);
    }

    public int getPlayerCount() {
        return SLS.proxy.getServer(id)
                .map(rs -> rs.getPlayersConnected().size())
                .orElse(0);
    }

    public SLSAction<List<String>> getLogs() {
        return client.getLogs();
    }

    public SLSAction<List<String>> getLogs(int size) {
        return client.getLogs(size);
    }

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

}
