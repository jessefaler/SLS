package net.slimelabs.vsls.routing;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.ArrayList;

public class Queue {

    private final int TIMEOUT = 40; // Seconds

    public Server server;
    ArrayList<Player> players = new ArrayList<>();
    private final AnimationController loadingIcon = new AnimationController();
    Runnable remove;

    public Queue(Server server, Runnable remove) {
        this.server = server;
        this.remove = remove;
        initListeners();
    }

    public void initListeners() {
        var unRegistration = server.onUnregistration(handle -> {
            handle.remove();
            flushQueueWithError();
        });
        server.onStatusChange(((status, handle) -> {
            if(status == ServerStatus.RUNNING) {
                unRegistration.remove();
                handle.remove();
                flushQueue();
            }
            if(status == ServerStatus.STOPPING || status == ServerStatus.OFFLINE) {
                unRegistration.remove();
                handle.remove();
                flushQueueWithError();
            }
        }));
    }

    public void enqueue(Player player) {
        players.add(player);
        loadingIcon.start(player);
        ProtoMessage.chat().add(MessagePreset.SLS).add("In queue for " + server.name, NamedTextColor.DARK_AQUA).sendMessage(player);
    }

    public boolean dequeue(Player player) {
        if(player == null) return false;
        ChatPackets.enableActionBarPackets(player.getUniqueId());
        loadingIcon.stop(player.getUniqueId());
        return players.remove(player);
    }

    public void flushQueue() {
        remove.run(); // remove this queue from the queue manager
        for(Player player : players) {
            ProtoMessage.actionBar().add("Joining " + server.name, NamedTextColor.GREEN).sendMessage(player);
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            Connector.connectPlayer(player, server.id);
            loadingIcon.stop(player.getUniqueId());
        }
    }

    public void flushQueueWithError() {
        remove.run(); // remove this queue from the queue manager
        for(Player player : players) {
            ProtoMessage.chat().add(MessagePreset.SLS).add("Failed to join " + server.name, NamedTextColor.RED).sendMessage(player);
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            loadingIcon.stop(player.getUniqueId());
        }
    }
}
