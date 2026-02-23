package net.slimelabs.vsls.matchmaking;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.entities.Blueprint;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.matchmaking.metadata.BlueprintMetadataParser;
import net.slimelabs.vsls.matchmaking.metadata.MatchmakingMetadata;
import net.slimelabs.vsls.matchmaking.registry.GameType;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles capacity checks, provisioning, and player assignment for a matchmaking pool.
 * Single synchronization point to avoid race conditions.
 */
public class AllocationEngine {

    private final MatchmakingPool pool;
    private final net.slimelabs.vsls.blueprints.BlueprintRegistry blueprintRegistry;
    private final BlueprintSelectionStrategy strategy;
    /** Players we've sent to this server but who may not be in getPlayerCount() yet. */
    private final Map<String, Integer> pendingAssignments = new ConcurrentHashMap<>();
    /** Player UUID -> server ID: players we've sent connect() to but not yet confirmed on server (so we can decrement pending on connect/disconnect). */
    private final Map<UUID, String> assignedInFlight = new ConcurrentHashMap<>();

    public AllocationEngine(
            MatchmakingPool pool,
            net.slimelabs.vsls.blueprints.BlueprintRegistry blueprintRegistry,
            BlueprintSelectionStrategy strategy) {
        this.pool = pool;
        this.blueprintRegistry = blueprintRegistry;
        this.strategy = strategy;
    }

    public synchronized void attemptAllocation() {
        while (!pool.waiting().isEmpty()) {
            Server server = findServerWithCapacity();
            if (server == null) {
                // Re-check once: getPlayerCount() can be briefly stale after a player leaves
                // (Velocity updates asynchronously), so we may have thought all servers were full.
                server = findServerWithCapacity();
                if (server != null) {
                    assignPlayers(server);
                    continue;
                }
                // Start as many servers as needed; prefer existing stopped servers over creating new ones
                int maxPlayers = getMaxPlayersForGameType();
                if (maxPlayers <= 0) return;
                int waiting = pool.waiting().size();
                int serversNeeded = (waiting + maxPlayers - 1) / maxPlayers;
                // Count both new servers being created and existing servers we're starting (in provisioning set)
                int alreadyInProgress = pool.getProvisioningInProgressCount() + pool.getProvisioning().size();
                int toStart = Math.max(0, serversNeeded - alreadyInProgress);
                for (int i = 0; i < toStart; i++) {
                    if (!tryStartExistingServer()) {
                        provisionNewServer();
                    }
                }
                return;
            }
            assignPlayers(server);
        }
    }

    private Server findServerWithCapacity() {
        GameType gameType = pool.getGameType();
        List<String> blueprintIds = gameType.getBlueprints().stream()
                .map(Blueprint::getId)
                .toList();

        // Clear stale pending for servers that are actually full (so effective count stays accurate)
        for (Server server : SLS.servers.getAll()) {
            if (server.getStatus() != ServerStatus.RUNNING) continue;
            if (!blueprintIds.contains(server.getBlueprintId())) continue;
            int max = getMaxPlayers(server);
            if (max > 0 && server.getPlayerCount() >= max) {
                pendingAssignments.remove(server.getId());
            }
        }

        // Among servers with capacity, pick the one with the most players (current + pending)
        return SLS.servers.getAll().stream()
                .filter(server -> server.getStatus() == ServerStatus.RUNNING)
                .filter(server -> blueprintIds.contains(server.getBlueprintId()))
                .filter(server -> getMaxPlayers(server) > 0)
                .map(server -> {
                    int max = getMaxPlayers(server);
                    int current = server.getPlayerCount();
                    int pending = pendingAssignments.getOrDefault(server.getId(), 0);
                    return new Object[] { server, current + pending, max };
                })
                .filter(pair -> (Integer) pair[1] < (Integer) pair[2]) // has capacity
                .max(Comparator.comparingInt(pair -> (Integer) pair[1])) // fullest first
                .map(pair -> (Server) pair[0])
                .orElse(null);
    }

    private int getMaxPlayers(Server server) {
        Blueprint bp = blueprintRegistry.getBlueprint(server.getBlueprintId());
        if (bp == null) return 0;
        MatchmakingMetadata meta = BlueprintMetadataParser.parse(bp);
        return meta != null ? meta.maxPlayers() : 0;
    }

    /** Max players per server for this pool's game type (from first blueprint with metadata). */
    private int getMaxPlayersForGameType() {
        var blueprints = pool.getGameType().getBlueprints();
        if (blueprints == null || blueprints.isEmpty()) return 0;
        for (Blueprint bp : blueprints) {
            MatchmakingMetadata meta = BlueprintMetadataParser.parse(bp);
            if (meta != null && meta.maxPlayers() > 0) return meta.maxPlayers();
        }
        return 0;
    }

    /** Existing server for this game type that is stopped (OFFLINE, STOPPING, PAUSED) and not already being started. */
    private Server findStoppedServerForGameType() {
        GameType gameType = pool.getGameType();
        List<String> blueprintIds = gameType.getBlueprints().stream()
                .map(Blueprint::getId)
                .toList();
        var provisioning = pool.getProvisioning();

        for (Server server : SLS.servers.getAll()) {
            if (!blueprintIds.contains(server.getBlueprintId())) continue;
            if (provisioning.contains(server)) continue; // already starting this one
            if (server.getStatus() != ServerStatus.RUNNING) {
                return server;
            }
        }
        return null;
    }

    /**
     * If an existing stopped server exists for this game type, start it and return true.
     * Caller will return; attemptAllocation() runs again when the server goes RUNNING.
     */
    private boolean tryStartExistingServer() {
        Server server = findStoppedServerForGameType();
        if (server == null) return false;

        GameType gameType = pool.getGameType();
        pool.addProvisioning(server);
        server.start().executeAsync(v -> {}, failure -> {
            pool.removeProvisioning(server);
            flushWaitingWithError("Failed to start server " + server.getName() + ": " + failure.getMessage());
        });
        server.getEvents().onStatusChange((status, handle) -> {
            if (status == ServerStatus.RUNNING) {
                handle.remove();
                pool.removeProvisioning(server);
                pool.addRunning(server);
                attemptAllocation();
            }
            if (status == ServerStatus.STOPPING || status == ServerStatus.OFFLINE) {
                handle.remove();
                pool.removeProvisioning(server);
                pool.removeRunning(server);
                pendingAssignments.remove(server.getId());
            }
        }).timeout(SLS.config.queue.timeout, TimeUnit.SECONDS, () -> {
            if (pool.getProvisioning().contains(server)) {
                pool.removeProvisioning(server);
                flushWaitingWithError("Failed to join " + gameType.getDisplayName() + ". Queue timed out.");
            }
        });
        return true;
    }

    private void provisionNewServer() {
        GameType gameType = pool.getGameType();
        Blueprint blueprint = strategy.select(gameType);
        if (blueprint == null) return;

        // Mark that we are provisioning a server before the async call returns
        pool.incrementProvisioningInProgress();

        var creation = SLS.api.createServer();
        creation.setBlueprintId(blueprint.getId());

        SLS.servers.createServer(creation).executeAsync(server -> {
            pool.addProvisioning(server);
            server.getEvents().onStatusChange((status, handle) -> {
                if (status == ServerStatus.RUNNING) {
                    handle.remove();
                    pool.removeProvisioning(server);
                    pool.addRunning(server);
                    pool.decrementProvisioningInProgress();
                    attemptAllocation();
                }
                if (status == ServerStatus.STOPPING || status == ServerStatus.OFFLINE) {
                    handle.remove();
                    pool.removeProvisioning(server);
                    pool.removeRunning(server);
                    pendingAssignments.remove(server.getId());
                    pool.decrementProvisioningInProgress();
                }
            }).timeout(SLS.config.queue.timeout, TimeUnit.SECONDS, () -> {
                if (pool.getProvisioning().contains(server)) {
                    pool.removeProvisioning(server);
                    pool.decrementProvisioningInProgress();
                    flushWaitingWithError("Failed to join " + gameType.getDisplayName() + ". Queue timed out.");
                }
            });
        }, failure -> {
            pool.decrementProvisioningInProgress();
            flushWaitingWithError("Failed to start server: " + failure.getMessage());
        });
    }

    private void flushWaitingWithError(String message) {
        QueuedPlayer q;
        while ((q = pool.waiting().poll()) != null) {
            pool.stopLoading(q.player());
            ProtoMessage.chat().add(MessagePreset.SLS).add(message, NamedTextColor.RED).sendMessage(q.player());
            ChatPackets.enableActionBarPackets(q.player().getUniqueId());
        }
    }

    /**
     * If a server with capacity exists, connect the player immediately and return true.
     * No queue message or loading icon. Returns false if no server has capacity.
     */
    public synchronized boolean tryConnectImmediately(Player player) {
        Server server = findServerWithCapacity();
        if (server == null) return false;
        int max = getMaxPlayers(server);
        if (max <= 0) return false;
        int current = server.getPlayerCount();
        int pending = pendingAssignments.getOrDefault(server.getId(), 0);
        if (current + pending >= max) return false;

        pendingAssignments.merge(server.getId(), 1, Integer::sum);
        assignedInFlight.put(player.getUniqueId(), server.getId());
        ChatPackets.enableActionBarPackets(player.getUniqueId());
        server.connect(player);
        return true;
    }

    private void assignPlayers(Server server) {
        int max = getMaxPlayers(server);
        if (max <= 0) return;
        int current = server.getPlayerCount();
        int pending = pendingAssignments.getOrDefault(server.getId(), 0);
        int slots = max - current - pending;
        if (slots <= 0) return;

        List<QueuedPlayer> toAssign = new ArrayList<>();
        QueuedPlayer q;
        while (toAssign.size() < slots && (q = pool.waiting().poll()) != null) {
            toAssign.add(q);
        }

        int assigned = toAssign.size();
        if (assigned == 0) return;

        pendingAssignments.merge(server.getId(), assigned, Integer::sum);
        String serverId = server.getId();

        for (QueuedPlayer qp : toAssign) {
            Player p = qp.player();
            assignedInFlight.put(p.getUniqueId(), serverId);
            pool.stopLoading(p);
            ProtoMessage.actionBar().add("Joining " + server.getName(), NamedTextColor.GREEN).sendMessage(p);
            ChatPackets.enableActionBarPackets(p.getUniqueId());
            server.connect(p);
        }
    }

    /**
     * Called when a player has connected to a server. If we assigned them via matchmaking,
     * decrement that server's pending count so effective count stays accurate.
     */
    void onPlayerConnectedToServer(Player player, Server server) {
        String serverId = server.getId();
        if (!serverId.equals(assignedInFlight.remove(player.getUniqueId()))) return;
        pendingAssignments.merge(serverId, 1, (cur, one) -> cur <= 1 ? 0 : cur - 1);
    }

    /**
     * Called when a player disconnects from the proxy. If they were assigned but never connected,
     * decrement pending so we don't keep counting them.
     */
    void onPlayerDisconnected(Player player) {
        String serverId = assignedInFlight.remove(player.getUniqueId());
        if (serverId == null) return;
        pendingAssignments.merge(serverId, 1, (cur, one) -> cur <= 1 ? 0 : cur - 1);
    }
}
