package net.slimelabs.vsls.routing;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Queue {

    private final int TIMEOUT = 120; // Seconds

    public Server server;
    ArrayList<Player> players = new ArrayList<>();
    private final AnimationController loadingIcon = new AnimationController();
    Runnable remove;
    private ScheduledTask timeoutTask;
    private boolean flushed = false;

    public Queue(Server server, Runnable remove) {
        this.server = server;
        this.remove = remove;
        initListeners();
        startTimeout();
    }

    private void startTimeout() {
        // Start a timeout task that will error if the server doesn't come online within the timeout period
        timeoutTask = SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
            if (!flushed) {
                flushQueueWithError();
            }
        }).delay(TIMEOUT, TimeUnit.SECONDS).schedule();
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    public void initListeners() {
        var unRegistration = server.onUnregistration(handle -> {
            handle.remove();
            cancelTimeout();
            flushQueueWithError();
        });
        server.onStatusChange(((status, handle) -> {
            if(status == ServerStatus.RUNNING) {
                unRegistration.remove();
                handle.remove();
                cancelTimeout();
                flushQueue();
            }
            // Don't immediately error on STOPPING/OFFLINE - wait for the server to come back online
            // or timeout. This allows reset operations to complete successfully.
        }));
    }

    public void enqueue(Player player) {
        players.add(player);
        loadingIcon.start(player);
        ProtoMessage.chat().add(MessagePreset.SLS).addMiniMessage("<gradient:#9d70ff:#00ffff>In queue for " + server.name + "</gradient>").sendMessage(player);
    }

    public boolean dequeue(Player player) {
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

    public void flushQueueWithError() {
        if (flushed) return;
        flushed = true;
        cancelTimeout();
        remove.run(); // remove this queue from the queue manager
        for(Player player : players) {
            ProtoMessage.chat().add(MessagePreset.SLS).add("Failed to join " + server.name, NamedTextColor.RED).sendMessage(player);
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            loadingIcon.stop(player.getUniqueId());
        }
    }
}
