package net.slimelabs.vsls.routing;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.ServerCrashEvent;
import com.protoxon.S4J.client.entities.StatusUpdateEvent;
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

public class Queue {

    public Server server;
    // Tracks if the server was created by a queue
    // This is so we know whether to stop the server or not if everyone leaves
    // The queue before the server starts
    private boolean queueCreated;
    ConcurrentLinkedQueue<Player> players = new ConcurrentLinkedQueue<>();
    private final Animation loadingIcon = new Animation();
    Runnable remove;
    private ScheduledTask timeoutTask;
    private final AtomicBoolean flushed = new AtomicBoolean(false);
    Event.Handle handle;

    public Queue(Server server, Runnable remove) {
        this.server = server;
        this.remove = remove;
        this.handle = initListeners();
        startTimeout();
    }

    // Sets if the server was created for this queue
    public void setQueueCreated(boolean value) {
        queueCreated = value;
    }

    // Returns true if the server in this queue was created by for the queue
    public boolean getQueueCreate() {
        return queueCreated;
    }

    private void startTimeout() {
        // Start a timeout task that will error if the server doesn't come online within the timeout period
        timeoutTask = SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
            if (!flushed.compareAndSet(false, true)) {
                return; // already flushed elsewhere
            }
            handle.remove();
            flushQueueWithError("Failed to join " + server.getName() + " queue timed out");
        }).delay(SLS.config.queue.timeout, TimeUnit.SECONDS).schedule();
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    public Event.Handle initListeners() {
        return server.getEvents().onStatusChange(((status, handle) -> {
            if(status == ServerStatus.RUNNING) {
                handle.remove();
                cancelTimeout();
                flushQueue();
            }
            if(status == ServerStatus.STOPPING || status == ServerStatus.OFFLINE) {
                handle.remove();
                cancelTimeout();
                flushQueueWithError("Failed to join " + server.getName());
            }
        })).timeout(SLS.config.queue.timeout, TimeUnit.SECONDS);
    }

    public void enqueue(Player player) {
        players.add(player);
        loadingIcon.start(player);
        ProtoMessage.chat().add(MessagePreset.SLS).addMiniMessage("<gradient:#9d70ff:#00ffff>In queue for " + server.getName() + "</gradient>").sendMessage(player);
    }

    public boolean dequeue(Player player) {
        if(player == null) return false;
        ChatPackets.enableActionBarPackets(player.getUniqueId());
        loadingIcon.stop(player.getUniqueId());
        boolean value = players.remove(player);
        if(players.isEmpty() || queueCreated) {
            // The queue is empty or this server was just spawned for it, so no players need it.
            // Stop the server asynchronously to avoid wasting resources on an idle instance.
            server.stop().executeAsync();
        }
        return value;
    }

    public void flushQueue() {
        if (!flushed.compareAndSet(false, true)) return;
        cancelTimeout();
        remove.run(); // remove this queue from the queue manager
        for(Player player : players) {
            ProtoMessage.actionBar().add("Joining " + server.getName(), NamedTextColor.GREEN).sendMessage(player);
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            Connector.connectPlayer(player, server.getShortId());
            loadingIcon.stop(player.getUniqueId());
        }
        players.clear();
    }

    public void flushQueueWithError(String message) {
        if (!flushed.compareAndSet(false, true)) return;
        cancelTimeout();
        remove.run(); // remove this queue from the queue manager
        for(Player player : players) {
            ProtoMessage.chat().add(MessagePreset.SLS).add(message, NamedTextColor.RED).sendMessage(player);
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            loadingIcon.stop(player.getUniqueId());
        }
        players.clear();
    }
}
