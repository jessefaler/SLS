package net.slimelabs.vsls.matchmaking.join;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects a player directly to a server by ID (short or prefix).
 * If the server is not RUNNING, starts it and queues the player until RUNNING,
 * using the same queue UX as matchmaking (loading icon, "In queue for X", timeout).
 */
public class DirectServerJoiner {

    private final Map<String, ServerConnectionQueue> queues = new ConcurrentHashMap<>();

    public void join(Player player, String serverId) {
        Server server = SLS.servers.getServer(serverId);
        if (server == null) {
            server = SLS.servers.resolve(serverId);
        }
        if (server == null) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .add("Error: Server not found", NamedTextColor.RED)
                    .sendMessage(player);
            return;
        }

        if (server.getStatus() == ServerStatus.RUNNING) {
            server.connect(player);
            return;
        }

        final Server s = server;
        ServerConnectionQueue queue = queues.computeIfAbsent(
                s.getId(),
                id -> new ServerConnectionQueue(s, () -> queues.remove(s.getId()), true)
        );
        queue.enqueue(player);
    }

    /**
     * Queues the player to connect when the server is RUNNING, without calling start().
     * Use for reset/restart flows where the server is already starting.
     */
    public void joinWhenReady(Player player, Server server) {
        if (server.getStatus() == com.protoxon.S4J.ServerStatus.RUNNING) {
            server.connect(player);
            return;
        }
        final Server s = server;
        ServerConnectionQueue queue = queues.computeIfAbsent(
                s.getId(),
                id -> new ServerConnectionQueue(s, () -> queues.remove(s.getId()), false)
        );
        queue.enqueue(player);
    }

    /** Removes the player from a direct-server queue if present. Returns true if they were queued. */
    public boolean dequeue(Player player) {
        for (ServerConnectionQueue queue : queues.values()) {
            if (queue.dequeue(player)) return true;
        }
        return false;
    }
}
