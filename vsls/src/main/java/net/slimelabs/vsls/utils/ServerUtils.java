package net.slimelabs.vsls.utils;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

public class ServerUtils {

    public static Server getServer(Player player) {
        Optional<ServerConnection> sc;
        sc = player.getCurrentServer();
        if(sc.isEmpty()) {
            return null;
        }
        // Get the name of the server the player is on
        String id = sc.get().getServerInfo().getName();
        return SLS.servers.resolve(id);
    }

    public static RegisteredServer getRegisteredServer(Player player) {
        Optional<ServerConnection> sc;
        sc = player.getCurrentServer();
        return sc.map(ServerConnection::getServer).orElse(null);
    }

    public static String getServerName(Player player) {
        Optional<ServerConnection> sc;
        sc = player.getCurrentServer();
        if(sc.isEmpty()) {
            return "null";
        }
        // Get the name of the server the player is on
        return sc.get().getServerInfo().getName();
    }

    /**
     * Returns a list of Player objects currently connected to the specified server.
     * If the server is not found or no players are connected, returns an empty list.
     *
     * @return a list of players
     */
    public static ArrayList<Player> getPlayers(String name) {
        return SLS.proxy.getServer(name)
                .map(rs -> new ArrayList<>(rs.getPlayersConnected()))
                .orElseGet(ArrayList::new);
    }

}
