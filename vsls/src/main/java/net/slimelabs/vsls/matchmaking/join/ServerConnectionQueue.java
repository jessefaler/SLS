package net.slimelabs.vsls.matchmaking.join;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
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
 */
public class ServerConnectionQueue {

    private final Server server;
    private final Runnable onClosed;
    private final ConcurrentLinkedQueue<Player> waiting = new ConcurrentLinkedQueue<>();
    private final Animation loadingIcon = new Animation();
    private final AtomicBoolean flushed = new AtomicBoolean(false);
    private ScheduledTask timeoutTask;
    private final Event.Handle statusHandle;

    /**
     * @param startServer if true, call server.start() when queue is created (e.g. direct join).
     *                    if false, only wait for RUNNING (e.g. reset/restart).
     */
    public ServerConnectionQueue(Server server, Runnable onClosed, boolean startServer) {
        this.server = server;
        this.onClosed = onClosed;
        statusHandle = server.getEvents().onStatusChange((status, handle) -> {
            if (status == ServerStatus.RUNNING) {
                handle.remove();
                flush();
            }
            if (status == ServerStatus.STOPPING || status == ServerStatus.OFFLINE) {
                handle.remove();
                flushWithError("Failed to join " + server.getName());
            }
        }).timeout(SLS.config.queue.timeout, TimeUnit.SECONDS, () -> {
            flushWithError("Failed to join " + server.getName() + ". Queue timed out.");
        });
        if (startServer) {
            server.start().executeAsync(v -> {}, failure -> {
                flushWithError("Failed to start server " + server.getName() + ": " + failure.getMessage());
            });
        }
    }

    private void flush() {
        if (!flushed.compareAndSet(false, true)) return;
        statusHandle.remove();
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
        onClosed.run();
        for (Player p : waiting) {
            ProtoMessage.chat().add(MessagePreset.SLS).add(message, NamedTextColor.RED).sendMessage(p);
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
            onClosed.run();
        }
        return removed;
    }

    public boolean isQueued(Player player) {
        return waiting.contains(player);
    }
}
