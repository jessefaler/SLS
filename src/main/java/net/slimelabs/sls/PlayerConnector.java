package net.slimelabs.sls;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Manages the connection and queuing of players to various servers.
 * This class is responsible for sending players to their designated servers
 * and handling any necessary queueing operations when servers are starting
 */
public class PlayerConnector {

    //Connects the player to the specified server if it is online
    public void joinServer(String name, Player player) {
        SLS.PROXY.getServer(name).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).connectWithIndication(),
                () -> player.sendMessage(Component.text("[§aSLS§r] Server not found.", NamedTextColor.DARK_RED))
        );
    }
}
