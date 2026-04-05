package net.slimelabs.vsls.matchmaking.join;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.exceptions.ApiFailure;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.events.Event;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.loader.Animation;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Queue for a single server: wait until RUNNING then connect all waiting players.
 * Same UX as matchmaking (loading icon, "In queue for X", timeout, status listener).
 * <p>
 * We do not flush on OFFLINE because: (1) During first-time install the server stays
 * offline until it starts—the panel often only emits STARTING then RUNNING, so we
 * may never receive an OFFLINE event; flushing on OFFLINE would wrongly kick players
 * who are waiting for install to finish. (2) When the server is deleted the panel
 * emits OFFLINE then a deletion event (no STOPPING); we subscribe to deletion and
 * flush with error there so players get a clear message.
 */
public class ServerConnectionQueue {

    private final Server server;
    /** If true, this queue called {@link Server#start()} abort boot if everyone leaves while still starting. */
    private final boolean startServer;
    private final Runnable onClosed;
    private final ConcurrentLinkedQueue<Player> waiting = new ConcurrentLinkedQueue<>();
    private final Animation loadingIcon = new Animation();
    private final AtomicBoolean flushed = new AtomicBoolean(false);
    private final Event.Handle statusHandle;
    private final Event.Handle deletionHandle;

    /**
     * @param startServer if true, call server.start() when queue is created (e.g. direct join).
     *                    if false, only wait for RUNNING (e.g. reset/restart).
     */
    public ServerConnectionQueue(Server server, Runnable onClosed, boolean startServer) {
        this.server = server;
        this.startServer = startServer;
        this.onClosed = onClosed;
        statusHandle = server.getEvents().onStatusChange((status, handle) -> {
            if (status == ServerStatus.RUNNING) {
                handle.remove();
                removalDeletionHandle();
                flush();
            }
            if (status == ServerStatus.STOPPING) {
                handle.remove();
                removalDeletionHandle();
                flushWithError("Failed to join " + server.getName());
            }
        }).timeout(SLS.config.queue.timeout, TimeUnit.SECONDS, () -> {
            removalDeletionHandle();
            flushWithError("Failed to join " + server.getName() + ". Queue timed out.");
        });
        deletionHandle = server.getEvents().onDeletion((deletion, handle) -> {
            flushWithError("Server " + server.getName() + " was deleted.");
        });
        if (startServer) {
            server.start().executeAsync(v -> {}, failure -> {
                removalDeletionHandle();
                flushWithApiError("Failed to start server " + server.getName(), failure);
            });
        }
    }

    private void removalDeletionHandle() {
        deletionHandle.remove();
    }

    private void flush() {
        if (!flushed.compareAndSet(false, true)) return;
        statusHandle.remove();
        deletionHandle.remove();
        onClosed.run();
        for (Player p : waiting) {
            ProtoMessage.actionBar().add("Joining " + server.getName(), NamedTextColor.GREEN).sendMessage(p);
            ChatPackets.enableActionBarPackets(p.getUniqueId());
            loadingIcon.stop(p.getUniqueId());
            server.connect(p);
        }
        waiting.clear();
    }

    private void flushWithError(String message) {
        if (!flushed.compareAndSet(false, true)) return;
        statusHandle.remove();
        deletionHandle.remove();
        onClosed.run();
        for (Player p : waiting) {
            ProtoMessage.chat().add(MessagePreset.SLS).add(message, NamedTextColor.RED).sendMessage(p);
            ChatPackets.enableActionBarPackets(p.getUniqueId());
            loadingIcon.stop(p.getUniqueId());
        }
        waiting.clear();
    }

    private void flushWithApiError(String message, ApiFailure failure) {
        if (!flushed.compareAndSet(false, true)) return;
        statusHandle.remove();
        deletionHandle.remove();
        onClosed.run();
        for (Player p : waiting) {
            Log.requestError(message, failure, p);
            ChatPackets.enableActionBarPackets(p.getUniqueId());
            loadingIcon.stop(p.getUniqueId());
        }
        waiting.clear();
    }

    public void enqueue(Player player) {
        waiting.add(player);
        loadingIcon.start(player);
        ProtoMessage.chat()
                .add(MessagePreset.SLS)
                .addMiniMessage("<gradient:#9d70ff:#00ffff>In queue for " + server.getName() + "</gradient>")
                .sendMessage(player);
    }

    /** Returns true if the player was in this queue. */
    public boolean dequeue(Player player) {
        loadingIcon.stop(player.getUniqueId());
        ChatPackets.enableActionBarPackets(player.getUniqueId());
        boolean removed = waiting.remove(player);
        if (removed && waiting.isEmpty()) {
            statusHandle.remove();
            deletionHandle.remove();
            onClosed.run();
            if (startServer && server.getStatus() == ServerStatus.STARTING) {
                server.stop().executeAsync(v -> {}, failure ->
                        Log.warn("Failed to stop server {} after queue emptied: {}", server.getName(), failure.info()));
            }
        }
        return removed;
    }

    public boolean isQueued(Player player) {
        return waiting.contains(player);
    }
}
