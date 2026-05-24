package net.slimelabs.vsls.matchmaking;

import com.velocitypowered.api.proxy.Player;
import net.slimelabs.vsls.blueprints.BlueprintRegistry;
import net.slimelabs.vsls.matchmaking.registry.GameType;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.loader.Animation;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MatchmakingPool {

    private final GameType gameType;
    private final Queue<QueuedPlayer> waiting = new ConcurrentLinkedQueue<>();
    private final Set<Server> running = ConcurrentHashMap.newKeySet();
    private final Set<Server> provisioning = ConcurrentHashMap.newKeySet();
    // Tracks servers that are being provisioned but may not yet exist in the provisioning set.
    private final AtomicInteger provisioningInProgress = new AtomicInteger(0);
    private final AllocationEngine allocator;
    private final Animation loadingIcon = new Animation();

    public MatchmakingPool(GameType gameType, BlueprintRegistry blueprintRegistry, BlueprintSelectionStrategy strategy) {
        this.gameType = gameType;
        this.allocator = new AllocationEngine(this, blueprintRegistry, strategy);
    }

    public GameType getGameType() {
        return gameType;
    }

    /** Queue used by AllocationEngine; do not modify from outside. */
    public Queue<QueuedPlayer> waiting() {
        return waiting;
    }

    public Set<Server> getRunning() {
        return Collections.unmodifiableSet(running);
    }

    public Set<Server> getProvisioning() {
        return Collections.unmodifiableSet(provisioning);
    }

    public void addProvisioning(Server server) {
        provisioning.add(server);
    }

    public void removeProvisioning(Server server) {
        provisioning.remove(server);
    }

    public void incrementProvisioningInProgress() {
        provisioningInProgress.incrementAndGet();
    }

    public void decrementProvisioningInProgress() {
        provisioningInProgress.decrementAndGet();
    }

    public boolean hasProvisioningInProgress() {
        return provisioningInProgress.get() > 0;
    }

    /** Number of servers currently being provisioned (not yet RUNNING). */
    public int getProvisioningInProgressCount() {
        return provisioningInProgress.get();
    }

    public void addRunning(Server server) {
        running.add(server);
    }

    public void removeRunning(Server server) {
        running.remove(server);
    }

    public void enqueue(Player player, String preferredBlueprintId) {
        if (allocator.tryConnectImmediately(player)) {
            return;
        }
        waiting.add(new QueuedPlayer(player, preferredBlueprintId));
        allocator.attemptAllocation();
        // Only show queue message and loading if still waiting (may have been assigned immediately)
        if (isQueued(player)) {
            loadingIcon.start(player);
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<gradient:#9d70ff:#00ffff>In queue for " + gameType.getDisplayName() + "</gradient>")
                    .sendMessage(player);
        }
    }

    /** Removes the player from the waiting queue. Returns true if they were queued. */
    public boolean dequeue(Player player) {
        ChatPackets.enableActionBarPackets(player.getUniqueId());
        loadingIcon.stop(player.getUniqueId());
        boolean removed = waiting.removeIf(qp -> qp.player().getUniqueId().equals(player.getUniqueId()));
        if (removed && waiting.isEmpty()) {
            allocator.cancelProvisioningWhenQueueEmpty();
        }
        return removed;
    }

    /** Returns true if the player is in this pool's waiting queue. */
    public boolean isQueued(Player player) {
        return waiting.stream().anyMatch(qp -> qp.player().getUniqueId().equals(player.getUniqueId()));
    }

    public void stopLoading(Player player) {
        loadingIcon.stop(player.getUniqueId());
    }

    /** Notify the allocator that a player connected to a server (so pending count can be reconciled). */
    public void onPlayerConnectedToServer(Player player, Server server) {
        allocator.onPlayerConnectedToServer(player, server);
    }

    /** Notify the allocator that a player disconnected (so in-flight assignment can be cleared). */
    public void onPlayerDisconnected(Player player) {
        allocator.onPlayerDisconnected(player);
    }
}
