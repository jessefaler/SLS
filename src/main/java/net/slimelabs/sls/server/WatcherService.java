package net.slimelabs.sls.server;

import com.velocitypowered.api.proxy.Player;
import net.slimelabs.sls.SLS;

public class WatcherService {

    public void watchServer(Player player, String serverName) {
        stopWatching(player); // Ensure the player is not watching any other server
        SLS.SERVER_REGISTRY.getServer(serverName).addWatcher(player);
    }

    public void stopWatching(Player player) {
        for(String serverName : SLS.SERVER_REGISTRY.getServerNames()) {
            SLS.SERVER_REGISTRY.getServer(serverName).removeWatcher(player);
        }
    }

    public boolean isWatching(Player player, String serverName) {
        return SLS.SERVER_REGISTRY.getServer(serverName).isWatching(player);
    }
}
