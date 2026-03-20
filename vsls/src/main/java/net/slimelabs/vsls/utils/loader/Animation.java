package net.slimelabs.vsls.utils.loader;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.packets.ChatPackets;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Animation {

    private final LoadingIcon icon = new LoadingIcon();
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    // Keeps track of players who are transferring between servers
    private final Set<UUID> switchingPlayers = ConcurrentHashMap.newKeySet();

    public Animation() {
        // Register the event listener
        SLS.proxy.getEventManager().register(SLS.plugin, this);
    }

    /** Start animating a loading bar for a player */
    public void start(Player player) {
        UUID id = player.getUniqueId();

        if (tasks.containsKey(id)) return; // already animating

        final int[] frame = {0};

        // Disable actionbar packets for this player
        // so servers cant display anything over the loading icon
        ChatPackets.disableActionBarPackets(player.getUniqueId());

        PluginContainer plugin = SLS.proxy.getPluginManager().fromInstance(SLS.plugin).orElse(null);
        if (plugin == null) {
            Log.error("Plugin instance not found while scheduling loading animation for player {}", player.getUsername());
            ChatPackets.enableActionBarPackets(player.getUniqueId());
            return;
        }

        ScheduledTask task = SLS.proxy.getScheduler()
                .buildTask(plugin, () -> {
                    // Only send the packet if the player is not in the middle of switching servers
                    // This fixes an issue where packets sent during the transfer process cause the player
                    // to lose connection to the proxy due to a DecoderException
                    if (!switchingPlayers.contains(id)) {
                        ChatPackets.sendSilentActionBarMessage(icon.getFrame(frame[0]++), player);
                    }
                }).repeat(72, TimeUnit.MILLISECONDS).schedule();

        tasks.put(id, task);
    }

    public void stop(UUID playerId) {
        ChatPackets.enableActionBarPackets(playerId);
        ScheduledTask task = tasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    @Subscribe
    // Stop animating for a player who disconnected
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        stop(player.getUniqueId());
        switchingPlayers.remove(player.getUniqueId());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        switchingPlayers.add(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        switchingPlayers.remove(event.getPlayer().getUniqueId());
    }

}
