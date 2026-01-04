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
        SLSAction<Void> action = client.stop();
        return action.map(v -> {
            unregister.run();
            return v;
        });
    }

    public SLSAction<Void> kill() {
        SLSAction<Void> action = client.kill();
        return action.map(v -> {
            unregister.run();
            return v;
        });
    }

    public SLSAction<Void> delete() {
        SLSAction<Void> action = client.delete();
        return action.map(v -> {
            unregister.run();
            return v;
        });
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

    // Internal
    public void handleStatusChange(ServerStatus status) {
    }

    public void handleCrash(ServerCrashEvent crashEvent) {

    }

    public void handleUnregistration() {
        // Notify Listeners
        fireUnregistration();
    }

    public void handleDeletion() {
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

}
