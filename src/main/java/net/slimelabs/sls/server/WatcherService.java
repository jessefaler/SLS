package net.slimelabs.sls.server;

import com.velocitypowered.api.proxy.Player;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.server.core.ServerInstance;

public class WatcherService {

    public void watchServer(Player player, String serverName) {
        stopWatching(player); // Ensure the player is not watching any other server
        SLS.SERVER_REGISTRY.getServer(serverName).addWatcher(player);
    }

    public void stopWatching(Player player) {
        for(ServerInstance serverInstance : SLS.SERVER_REGISTRY.getAllServers()) {
            serverInstance.removeWatcher(player);
        }
    }

    /**
     * Retrieves the name of the server that the specified player is currently watching.
     *
     * @param player the player whose watched server name is to be retrieved
     * @return the name of the server the player is watching, or {@code null} if the player is not watching any server
     */
    public String getWatchingServer(Player player) {
        for(ServerInstance serverInstance : SLS.SERVER_REGISTRY.getAllServers()) {
            if(serverInstance.isWatching(player)) {
                return serverInstance.name;
            }
        }
        return null;
    }

    /**
     * Checks if a player is watching a specific server
     * @param player the player to check
     * @param serverName the name of the server
     * @return true, if they are watching the sever
     */
    public boolean isWatching(Player player, String serverName) {
        return SLS.SERVER_REGISTRY.getServer(serverName).isWatching(player);
    }

    /**
     * Checks if a player is watching any server
     * @param player the player to check
     * @return true, if they are watching the sever
     */
    public boolean isWatching(Player player) {
        for(ServerInstance serverInstance : SLS.SERVER_REGISTRY.getAllServers()) {
            if(serverInstance.isWatching(player)) {
                return true;
            }
        }
        return false;
    }
}
