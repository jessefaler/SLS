package net.slimelabs.vsls.matchmaking.join;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.matchmaking.metadata.BlueprintMetadataParser;
import net.slimelabs.vsls.matchmaking.metadata.MatchmakingMetadata;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.CommandMessageParts;
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
        join(player, serverId, false);
    }

    public void join(Player player, String serverId, boolean ignoreBlueprintRules) {
        Server server = SLS.servers.getServer(serverId);
        if (server == null) {
            server = SLS.servers.resolve(serverId);
        }
        if (server == null) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<red>Server not found.</red>")
                    .sendMessage(player);
            return;
        }

        ServerConnectionQueue existingQueue = queues.get(server.getId());
        if (!ignoreBlueprintRules && !hasCapacity(player, server, existingQueue)) {
            return;
        }

        if (server.getStatus() == ServerStatus.RUNNING) {
            server.connect(player);
            return;
        }

        final Server s = server;
        ServerConnectionQueue existingForTarget = existingQueue;
        if (existingForTarget != null && existingForTarget.isQueued(player)) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<gray>You are already in queue for</gray> " + CommandMessageParts.server(s) + "<gray>.</gray>")
                    .sendMessage(player);
            return;
        }
        dequeue(player);

        ServerConnectionQueue queue = queues.computeIfAbsent(
                s.getId(),
                id -> new ServerConnectionQueue(s, () -> queues.remove(s.getId()), true)
        );
        queue.enqueue(player, ignoreBlueprintRules);
    }

    /**
     * Queues the player to connect when the server is RUNNING, without calling start().
     * Use for reset/restart flows where the server is already starting.
     */
    public void joinWhenReady(Player player, Server server) {
        ServerConnectionQueue existingForTarget = queues.get(server.getId());
        if (!hasCapacity(player, server, existingForTarget)) {
            return;
        }
        if (server.getStatus() == com.protoxon.S4J.ServerStatus.RUNNING) {
            server.connect(player);
            return;
        }
        final Server s = server;
        if (existingForTarget != null && existingForTarget.isQueued(player)) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<gray>You are already in queue for</gray> " + CommandMessageParts.server(s) + "<gray>.</gray>")
                    .sendMessage(player);
            return;
        }
        dequeue(player);

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

    private boolean hasCapacity(Player player, Server server, ServerConnectionQueue queue) {
        int maxPlayers = getMaxPlayers(server);
        if (maxPlayers <= 0) return true;

        int online = server.getPlayerCount();
        int queued = queue != null ? queue.getWaitingCount() : 0;
        if (online + queued < maxPlayers) return true;

        ProtoMessage.chat()
                .add(MessagePreset.SLS)
                .addMiniMessage("<red>Server full:</red> " + CommandMessageParts.server(server)
                        + " <gray>(" + online + " online")
                .addMiniMessage(queued > 0 ? ", " + queued + " queued" : "")
                .addMiniMessage(", max " + maxPlayers + ").</gray>")
                .sendMessage(player);
        ProtoMessage.actionBar()
                .add("Server full: " + server.getCompositeId(), NamedTextColor.RED)
                .sendMessage(player);
        return false;
    }

    private int getMaxPlayers(Server server) {
        var blueprint = SLS.blueprints.getBlueprint(server.getBlueprintId());
        if (blueprint == null) return 0;
        MatchmakingMetadata metadata = BlueprintMetadataParser.parse(blueprint);
        return metadata != null ? metadata.maxPlayers() : 0;
    }
}
