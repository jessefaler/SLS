package net.slimelabs.sls;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import net.kyori.adventure.text.Component;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A class for intercepting and modifying inbound and outbound packets <p><p/>
 * For API documentation, visit: <a href="https://javadocs.packetevents.com/">Packetevents API Docs</a> <p>
 * For detailed protocol information, refer to the Minecraft Protocol Wiki: <a href="https://wiki.vg/Protocol">Protocol Wiki</a>
 */
public class PacketListener implements com.github.retrooper.packetevents.event.PacketListener {
    private final Set<UUID> disabledActionBars = new HashSet<>();
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
    public void enableActionBarPackets(UUID uuid) {
        disabledActionBars.remove(uuid);
    }

    /**
     * Disables the action bar packet being sent to the users client
     * @param uuid the uuid of the player
     */
    public void disableActionBarPackets(UUID uuid) {
        disabledActionBars.add(uuid);
    }

    /**
     * Sends an action bar message to a player without triggering listeners.
     * @param component the message component to send
     * @param uuid the players uuid
     */
    public void sendSilentActionBarMessage(Component component, UUID uuid) {
        WrapperPlayServerActionBar actionBarPacket = new WrapperPlayServerActionBar(component); // build the packet
        User user = PacketEvents.getAPI().getPlayerManager().getUser(uuid); // Get the user
        user.sendPacketSilently(actionBarPacket); // Send the packet
    }
}
