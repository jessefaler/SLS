package net.slimelabs.vsls.server.lifecycle;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.server.ServerProvider;
import net.slimelabs.vsls.utils.ServerUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LifecycleManager {

    private final ServerProvider provider;

    // Tracks servers currently shutting down
    private final Set<String> shuttingDown = ConcurrentHashMap.newKeySet();

    // Tracks scheduled stop tasks so they can be canceled
    private final Map<String, ScheduledTask> pendingStops = new ConcurrentHashMap<>();

    public LifecycleManager(ServerProvider provider) {
        this.provider = provider;
    }

    /**
     * Starts the server lifecycle manager
     */
    public void start() {
        SLS.proxy.getEventManager().register(SLS.plugin, this);

        // Run a scheduled check on all servers
        SLS.proxy.getScheduler()
                .buildTask(SLS.plugin, () -> {
                    for (Server server : provider.getAll()) {
                        if (server.getStatus() == ServerStatus.RUNNING
                                && server.getPlayerCount() == 0
                                && server.isLifecycleEnabled()) {
                            server.getStats().executeAsync(stats -> {
                                if (stats.getUptime() > Duration.ofMinutes(1).toMillis()
                                        && server.getPlayerCount() == 0
                                        && server.getStatus() == ServerStatus.RUNNING
                                        && server.isLifecycleEnabled()) {
                                    scheduleStop(server.getCompositeId());
                                }
                            });
                        }
                    }
                })
                .repeat(SLS.config.lifecycle.check_interval, TimeUnit.MINUTES)
                .schedule();
    }

    /**
     * Player switching servers
     */
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        // OriginalServer is the server the player is attempting to connect to
        // Make sure the server they are trying to connect to is not pending to stop
        cancelPendingStop(event.getOriginalServer().getServerInfo().getName());

        // Previous Server is the server the player is coming from (null if they just joined the proxy)
        // Check if it is empty if so shut it down assuming it is an SLS managed server
        RegisteredServer previous = event.getPreviousServer();
        if (previous != null) {
            delayedCheck(previous);
        }
    }

    /**
     * Player disconnecting from proxy
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // Run a check on the backed server the player was connected to before they left the proxy
        event.getPlayer().getCurrentServer().ifPresent(current -> {
            delayedCheck(current.getServer());
        });
    }

    /**
     * Schedule a cancelable stop for a server that may now be empty.
     */
    private void delayedCheck(RegisteredServer rs) {
        String id = rs.getServerInfo().getName();
        Server server = provider.resolve(id);
        if (server == null || !server.isLifecycleEnabled()) {
            return;
        }
        scheduleStop(id);
    }

    /**
     * Schedule a cancelable stop after {@code stop_delay} if one is not already pending.
     * Emptiness is re-checked when the task fires so leave-time player counts can settle.
     */
    private void scheduleStop(String id) {
        if (shuttingDown.contains(id) || pendingStops.containsKey(id)) {
            return;
        }

        Server server = provider.resolve(id);
        if (server == null || !server.isLifecycleEnabled()) {
            return;
        }

        AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        ScheduledTask task = SLS.proxy.getScheduler()
                .buildTask(SLS.plugin, () -> {
                    ScheduledTask self = taskRef.get();
                    if (self == null || pendingStops.get(id) != self) {
                        return; // cancelled or replaced before we ran
                    }

                    Server current = provider.resolve(id);
                    if (current == null
                            || !current.isLifecycleEnabled()
                            || current.getStatus() != ServerStatus.RUNNING
                            || !ServerUtils.getPlayers(id).isEmpty()) {
                        pendingStops.remove(id, self);
                        return;
                    }

                    // Claim the pending stop; if this fails a join cancelled us mid-check
                    if (!pendingStops.remove(id, self)) {
                        return;
                    }

                    shutdown(current);
                })
                .delay(SLS.config.lifecycle.stop_delay, TimeUnit.SECONDS)
                .schedule();
        taskRef.set(task);

        ScheduledTask existing = pendingStops.putIfAbsent(id, task);
        if (existing != null) {
            task.cancel();
            return;
        }

        Log.debug("Lifecycle Manager: Scheduled shutdown for empty server " + id
                + " in " + SLS.config.lifecycle.stop_delay + "s");
    }

    /**
     * Cancel a pending stop when a player joins (or is joining) the server.
     */
    private void cancelPendingStop(String id) {
        ScheduledTask task = pendingStops.remove(id);
        if (task != null) {
            task.cancel();
            shuttingDown.remove(id);
            Log.debug("Lifecycle Manager: Cancelled shutdown for server " + id + " (player joined)");
        }
    }

    /**
     * Stop an empty server if not already shutting down
     */
    private void shutdown(Server server) {
        String id = server.getCompositeId();

        // Prevent duplicate shutdown
        if (!shuttingDown.add(id)) {
            return;
        }

        ScheduledTask pending = pendingStops.remove(id);
        if (pending != null) {
            pending.cancel();
        }

        Log.debug("Lifecycle Manager: Shutting down empty server " + id);
        server.sendCommand("say [SLS] Server is empty. Shutting down...");

        server.stop().executeAsync(success -> {
            shuttingDown.remove(id);
        }, failure -> {
            shuttingDown.remove(id);
            Log.warn("Lifecycle Manager: Failed to stop server " + id);
        });
    }
}
