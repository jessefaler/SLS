package net.slimelabs.vsls.routing;

import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

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
        ServerCreationAction creation = SLS.api.createServer();
        creation.setBlueprintId(blueprintId);
        SLS.servers.CreateServer(creation).executeAsync(server -> {
            SLS.queue.enqueue(player, server);
        }, failure -> {
            Log.error("Failed to start server from blueprint {} reason: {}", blueprintId, failure.getMessage());
        });
    }

}
