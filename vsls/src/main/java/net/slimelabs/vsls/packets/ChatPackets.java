package net.slimelabs.vsls.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A class for intercepting and modifying inbound and outbound actionbar chat packets <p><p/>
 * - Utilizes the PacketEvents API -
 * For API documentation, visit: <a href="https://javadocs.packetevents.com/">Packetevents API Docs</a> <p>
 * For detailed protocol information, refer to the Minecraft Protocol Wiki: <a href="https://wiki.vg/Protocol">Protocol Wiki</a>
 * <p></p>
 * This class is primarily used to overide a players actionbar when displaying the loading animation ( ▂▃▅▆▇▆▅▃▂ )
 * that way servers cant display their own messages while the loading icon is being displayed
 */
public class ChatPackets implements PacketListener {

    // Keeps a list of player uuid's who have their actionbars disabled
    private static final Set<UUID> disabledActionBars = new HashSet<>();

    /**
     * Initializes the packet events api and registers the packet listener
     */
    public static ChatPackets init() {
        // Register packet listeners
        ChatPackets chatPackets = new ChatPackets();
        PacketEvents.getAPI().getEventManager().registerListener(chatPackets, PacketListenerPriority.NORMAL);
        PacketEvents.getAPI().init(); // Initialize PacketEvents API
        return chatPackets;
    }

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
     * This can be used to send a player who has their actionbar packets disabled an actionbar message
     * @param component the message component to send
     * @param player the player
     */
    public static void sendSilentActionBarMessage(Component component, Player player) {
        WrapperPlayServerActionBar actionBarPacket = new WrapperPlayServerActionBar(component); // build the packet
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player); // Get the user
        user.sendPacketSilently(actionBarPacket); // Send the packet
    }
}
