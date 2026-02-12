package net.slimelabs.vsls.routing;

import com.velocitypowered.api.proxy.Player;
import net.slimelabs.vsls.server.Server;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class QueueManager {
    private ConcurrentHashMap<String, Queue> queues = new ConcurrentHashMap<>();

    public Queue enqueue(Player player, Server server) {
        Queue queue = getQueue(server);
        queue.enqueue(player);
        return queue;
    }

    public void dequeue(Player player, Server server) {
        Queue queue = queues.get(server.getId());
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
        Queue queue = queues.get(server.getId());
        if(queue == null) {
            queue = new Queue(server, () -> {
                remove(server.getId());
            });
            queues.put(server.getId(), queue);
        }
        return queue;
    }

    public Collection<Queue> getQueues() {
        return queues.values();
    }
}
