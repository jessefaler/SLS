package net.slimelabs.vsls.channel;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;

import java.util.Optional;
import java.util.UUID;

/**
 * MessageChannel handles incoming plugin messages over Velocity's messaging system.
 * <p>
 * This channel listens for messages sent from player connections on the "sls:vsls" channel
 * and routes them to the appropriate handler based on the provided MessageType.
 * <p>
 * Message Format (semicolon-delimited):
 *   <TYPE>;<PLAYER_UUID>;<ARGUMENT>
 * <p>
 * Examples:
 *   JOIN_BLUEPRINT;<uuid>;<blueprintId>
 *   JOIN_SERVER;<uuid>;<serverId>
 * <p>
 * This acts as a lightweight RPC layer, allowing backend services or servers
 * to request actions (such as server joins) to be executed on the proxy.
 */
public class MessageChannel {

    public static final ChannelIdentifier SLS_CHANNEL = MinecraftChannelIdentifier.create("sls", "vsls");

    public static class SLSMessageListener {
        @Subscribe
        public void onPluginMessage(PluginMessageEvent event) {

            Log.error("REC: MSG");
            if (!event.getIdentifier().equals(SLS_CHANNEL)) {
                return;
            }
            Log.error("REC: CHANNEL MSG");

            String resultString = new String(event.getData());
            String[] parts = resultString.split(";");
            // Get the message type
            MessageType type = MessageType.valueOf(parts[0]);
            // get the players id
            UUID playerUUID = UUID.fromString(parts[1]);

            // Get the player using the provided uuid
            Optional<Player> player = SLS.proxy.getPlayer(playerUUID);
            if(player.isEmpty()) {
                Log.error("SLS messaging channel: could not get player from provided uuid");
                return;
            }

            switch (type) {
                case JOIN_BLUEPRINT -> {
                    String blueprintId = parts[2];
                    // Join the player to the provided blueprint
                    SLS.joinService.joinBlueprint(player.get(), blueprintId);
                }
                case JOIN_SERVER -> {
                    String serverId = parts[2];
                    // Join the player to the provided server
                    SLS.joinService.joinServer(player.get(), serverId);
                }
            }
        }
    }
}
