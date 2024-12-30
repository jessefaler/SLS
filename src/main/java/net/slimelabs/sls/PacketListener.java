package net.slimelabs.sls;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A class for intercepting and modifying inbound and outbound packets <p><p/>
 * - Utilizes the PacketEvents API -
 * For API documentation, visit: <a href="https://javadocs.packetevents.com/">Packetevents API Docs</a> <p>
 * For detailed protocol information, refer to the Minecraft Protocol Wiki: <a href="https://wiki.vg/Protocol">Protocol Wiki</a>
 */
public class PacketListener implements com.github.retrooper.packetevents.event.PacketListener {
    private static final Set<UUID> disabledActionBars = new HashSet<>();
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ACTION_BAR) {
            if(disabledActionBars.contains(event.getUser().getUUID())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Enables the action bar packet being sent to the users client
     * @param uuid the uuid of the player
     */
    public static void enableActionBarPackets(UUID uuid) {
        disabledActionBars.remove(uuid);
    }

    /**
     * Disables the action bar packet being sent to the users client
     * @param uuid the uuid of the player
     */
    public static void disableActionBarPackets(UUID uuid) {
        disabledActionBars.add(uuid);
    }

    /**
     * Sends an action bar message to a player without triggering listeners.
     * @param component the message component to send
     * @param player the player
     */
    public static void sendSilentActionBarMessage(Component component, Player player) {
        WrapperPlayServerActionBar actionBarPacket = new WrapperPlayServerActionBar(component); // build the packet
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player); // Get the user
        user.sendPacketSilently(actionBarPacket); // Send the packet
    }


    // Test method not complete
    public void setInvisible(Player targetPlayer) {
        for (Player player : SLS.PROXY.getAllPlayers()) {
            int entityID = PacketEvents.getAPI().getPlayerManager().getUser(player).getEntityId();
            WrapperPlayServerEntityEffect packet = new WrapperPlayServerEntityEffect(entityID, PotionTypes.INVISIBILITY, 1, 1, (byte) 0);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        }
    }



}
