package net.slimelabs.vsls.routing;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.entities.Blueprint;
import com.protoxon.S4J.requests.Route;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.blueprints.BlueprintRegistry;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Objects;

public class Connector {

    /**
     * Connects a player to the server with the specified id
     * Logs failures to the player and console
     * @param player the player to connect to the server
     * @param id the id of the server to connect to
     */
    public static void connectPlayer(Player player, String id) {
        SLS.proxy.getServer(id).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).connectWithIndication().thenAccept(connection -> {
                }).exceptionally(throwable -> {
                    // Handle connection failure
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Error: Could not connect to " + id, NamedTextColor.RED)
                            .sendMessage(player);
                    Log.withField("reason", throwable.getMessage()).error("Failed to connect {} to {}", player.getUsername(), id);
                    return null;
                }),
                () -> ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("Error: Server not found", NamedTextColor.RED)
                        .sendMessage(player)
        );
    }

    public static void join(Player player, String blueprintId) {
        Queue currentQueue = SLS.queue.getQueue(player);
        if(currentQueue != null) {
            if(Objects.equals(currentQueue.server.blueprintId, blueprintId)) {
                // If the player is trying to queue to the same server they are currently queued for send a message and exit
                ProtoMessage.chat().add(MessagePreset.SLS).addMiniMessage("<gradient:#9d70ff:#00ffff>You are already in queue for " + currentQueue.server.name + "</gradient>").sendMessage(player);
                return;
            }
            // If the player is currently in queue for a different server dequeue them
            currentQueue.dequeue(player);
        }

        // Check running servers
        for (Server server : SLS.servers.getAll()) {
            if (Objects.equals(server.blueprintId, blueprintId)) {
                if(server.status == ServerStatus.RUNNING) {
                    connectPlayer(player, server.getId());
                    return;
                }
            }
        }

        // Check existing queues
        for (Queue queue : SLS.queue.getQueues()) {
            if (Objects.equals(queue.server.blueprintId, blueprintId)) {
                queue.enqueue(player);
                return;
            }
        }

        // Check paused servers
        for (Server server : SLS.servers.getAll()) {
            if (Objects.equals(server.blueprintId, blueprintId)) {
                if(server.status == ServerStatus.PAUSED) {
                    server.unpause().executeAsync(success -> {
                        connectPlayer(player, server.getId());
                    }, failure -> {
                        ProtoMessage.chat().add(MessagePreset.SLS)
                                .add("Failed to join " + server.name + " \n  - failed to unpause server container", NamedTextColor.RED)
                                .sendMessage(player);
                    });
                    return;
                }
            }
        }

        // Otherwise create the server
        ServerCreationAction creation = SLS.api.createServer();
        creation.setBlueprintId(blueprintId);

        SLS.servers.createServer(creation).executeAsync(server -> {
            SLS.queue.enqueue(player, server).setQueueCreated(true);
        }, failure -> {
            ProtoMessage.chat()
                    .add("Failed to join server " + (SLS.blueprints.getBlueprint(blueprintId)
                            != null ? SLS.blueprints.getBlueprint(blueprintId).getName() : blueprintId)
                            + " reason: " + failure.getMessage(), NamedTextColor.RED)
                    .sendMessage(player);
            Log.error("Failed to start server from blueprint {} reason: {}", blueprintId, failure.getMessage());
        });
    }

}
