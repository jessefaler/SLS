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

        // Run a check on all servers every 5 minutes
        SLS.proxy.getScheduler()
                .buildTask(SLS.plugin, () -> {
                    for (Server server : provider.getAll()) {
                        if(server.getStatus() == ServerStatus.RUNNING) {
                            if (server.getPlayerCount() == 0) {
                                server.getStats().executeAsync(stats -> {
                                    if (stats.getUptime() > Duration.ofMinutes(1).toMillis()
                                            && server.getPlayerCount() == 0
                                            && server.getStatus() == ServerStatus.RUNNING) {
                                        shutdown(server);
                                    }
                                });
                            }
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
        String id = event.getOriginalServer().getServerInfo().getName();
        ScheduledTask task = pendingStops.remove(id);
        if (task != null) {
            task.cancel();
            shuttingDown.remove(id);
            Log.info("Lifecycle Manager: Cancelled shutdown for server " + id + " (player joined)");
        }

        // PreviousServer is the server the player is coming from (null if they just joined the proxy)
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
     * Perform delayed emptiness check
     */
    private void delayedCheck(RegisteredServer rs) {
        SLS.proxy.getScheduler()
                .buildTask(SLS.plugin, () -> {
                    String id = rs.getServerInfo().getName();
                    int playerCount = ServerUtils.getPlayers(id).size();

                    if (playerCount == 0) {
                        Server server = provider.resolve(id);
                        if (server != null) {
                            if(server.getStatus() == ServerStatus.RUNNING) {
                                shutdown(server);
                            }
                        }
                    }
                })
                .delay(SLS.config.lifecycle.stop_delay, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Schedule shutdown if not already scheduled
     */
    private void shutdown(Server server) {
        String id = server.getCompositeId();

        // Prevent duplicate shutdown scheduling
        if (!shuttingDown.add(id)) {
            return;
        }

        Log.info("Lifecycle Manager: Shutting down empty server " + id);
        server.sendCommand("say [SLS] Server is empty. Shutting down...");

        // Stop immediately
        server.stop().executeAsync(success -> {}, failure -> {
            Log.warn("Lifecycle Manager: Failed to stop server " + id);
        });

        // Cleanup
        shuttingDown.remove(id);
        pendingStops.remove(id);
    }
}