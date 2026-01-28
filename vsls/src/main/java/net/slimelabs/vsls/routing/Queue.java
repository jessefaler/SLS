package net.slimelabs.vsls.routing;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.server.Listener;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.sql.Time;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Queue {

    public Server server;
    ArrayList<Player> players = new ArrayList<>();
    private final AnimationController loadingIcon = new AnimationController();
    Runnable remove;
    private ScheduledTask timeoutTask;
    private boolean flushed = false;
    Listener.Handle handle;

    public Queue(Server server, Runnable remove) {
        this.server = server;
        this.remove = remove;
        this.handle = initListeners();
        startTimeout();
    }

    private void startTimeout() {
        // Start a timeout task that will error if the server doesn't come online within the timeout period
        timeoutTask = SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
            if (!flushed) {
                handle.remove();
                flushQueueWithError("Failed to join " + server.name + " queue timed out");
            }
        }).delay(SLS.config.queue.timeout, TimeUnit.SECONDS).schedule();
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    public Listener.Handle initListeners() {
        return server.onStatusChange(((status, handle) -> {
            if(status == ServerStatus.RUNNING) {
                handle.remove();
                cancelTimeout();
                flushQueue();
            }
            if(status == ServerStatus.STOPPING || status == ServerStatus.OFFLINE) {
                handle.remove();
                cancelTimeout();
                flushQueueWithError("Failed to join " + server.name);
            }
        })).timeout(SLS.config.queue.timeout, TimeUnit.SECONDS);
    }

    public synchronized void enqueue(Player player) {
        players.add(player);
        loadingIcon.start(player);
        ProtoMessage.chat().add(MessagePreset.SLS).addMiniMessage("<gradient:#9d70ff:#00ffff>In queue for " + server.name + "</gradient>").sendMessage(player);
    }

    public synchronized boolean dequeue(Player player) {
        if(player == null) return false;
        ChatPackets.enableActionBarPackets(player.getUniqueId());
        loadingIcon.stop(player.getUniqueId());
        return players.remove(player);
    }

    public void flushQueue() {
        if (flushed) return;
        flushed = true;
        cancelTimeout();
        remove.run(); // remove this queue from the queue manager
        for(Player player : players) {
            ProtoMessage.actionBar().add("Joining " + server.name, NamedTextColor.GREEN).sendMessage(player);
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            Connector.connectPlayer(player, server.id);
            loadingIcon.stop(player.getUniqueId());
        }
    }

    public void flushQueueWithError(String message) {
        if (flushed) return;
        flushed = true;
        cancelTimeout();
        remove.run(); // remove this queue from the queue manager
        for(Player player : players) {
            ProtoMessage.chat().add(MessagePreset.SLS).add(message, NamedTextColor.RED).sendMessage(player);
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            loadingIcon.stop(player.getUniqueId());
        }
    }
}
