package net.slimelabs.vsls.routing;

import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.server.Listener;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class QueueManager {
    private ConcurrentHashMap<String, Queue> queues = new ConcurrentHashMap<>();

    public void enqueue(Player player, Server server) {
        Queue queue = getQueue(server);
        queue.enqueue(player);
    }

    public void dequeue(Player player, Server server) {
        Queue queue = queues.get(server.id);
        if(queue == null) return;
        queue.dequeue(player);
    }

    public Queue getQueue(Player player) {
        for(Queue queue : queues.values()) {
            if(queue.players.contains(player)) {
                return queue;
            }
        }
        return null;
    }

    public boolean dequeue(Player player) {
        for(Queue queue : queues.values()) {
            boolean success = queue.dequeue(player);
            if(success) return true;
        }
        return false;
    }

    public void remove(String id) {
        queues.remove(id);
    }

    // Return a server's queue or creates it if it doesn't exist
    public Queue getQueue(Server server) {
        Queue queue = queues.get(server.id);
        if(queue == null) {
            queue = new Queue(server, () -> {
                remove(server.id);
            });
            queues.put(server.id, queue);
        }
        return queue;
    }
}
