package net.slimelabs.sls;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessagePreset;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static net.slimelabs.sls.PacketListener.*;

// Handles the queueing of players in the PlayerConnector class

public class QueueService {
    private ScheduledTask task;
    private int count;
    private int max;

    public void queuePlayerToJoinServer(String serverName, CommandSource source) {
        Player player = (Player) source;
        UUID uuid = player.getUniqueId();
        SLS.PLAYER_CONNECTOR.queue.put(uuid, serverName);
        disableActionBarPackets(player.getUniqueId()); // Disable action bar packets
        Message.chat().add(MessagePreset.SLS).add(" In queue for " + serverName.replace("_", " "), NamedTextColor.DARK_AQUA).sendMessage(source);
        task = SLS.PROXY.getScheduler().buildTask(SLS.PLUGIN, () -> {
            if (!player.isActive()) {//player disconnected from the server while in queue.
                SLS.PLAYER_CONNECTOR.queue.remove(uuid);//remove them from the players in queue map
                //if no other players are in queue for this server stop it from starting
                if (!SLS.PLAYER_CONNECTOR.queue.containsValue(serverName)) {
                    SLS.SERVER_REGISTRY.shutdownServer(serverName);
                }
                enableActionBarPackets(player.getUniqueId());
                task.cancel();//cancel this task
                return;
            }
            Component textComponent = null;
            if (SLS.SERVER_REGISTRY.isOnline(serverName)) {
                Message.actionBar().add("Joining " + serverName.replace("_", " "), NamedTextColor.GREEN).sendMessage(source);
                SLS.PLAYER_CONNECTOR.queue.remove(uuid);
                SLS.PROXY.getServer(serverName).ifPresentOrElse(
                        targetServer -> player.createConnectionRequest(targetServer).connect().thenAccept(connection -> {
                            enableActionBarPackets(player.getUniqueId());
                            Message.actionBar().sendMessage(source); // Send a blank message to clear their actionbar
                        }).exceptionally(throwable -> {
                            // Handle connection failure
                            Message.chat()
                                    .add(MessagePreset.SLS)
                                    .add(" Error: Could not connect to " + serverName, NamedTextColor.RED)
                                    .sendMessage(source);
                            SLS.LOGGER.error("There was an error while connecting {} to {} ensure the ip and port are configured correctly and the server is accessible through velocity", player.getUsername(), serverName);
                            throwable.printStackTrace();
                            return null;
                        }),
                        () -> Message.chat()
                                .add(MessagePreset.SLS)
                                .add(" Error: Server not found", NamedTextColor.RED)
                                .sendMessage(source)
                );
                task.cancel();
                return;
            }
            if (SLS.SERVER_REGISTRY.failedToStart(serverName)) {
                Message.chat().add(MessagePreset.SLS).add(" Failed to join " + serverName.replace("_", " "), NamedTextColor.RED).sendMessage(source);
                SLS.PLAYER_CONNECTOR.queue.remove(uuid);
                enableActionBarPackets(player.getUniqueId());
                task.cancel();
                return;
            }
            //show a loading animation in action bar
            count++;
            max++;
            if (max > 1714) { // Request times out in ~2 minutes
                Message.chat().add(MessagePreset.SLS).add(" Failed to join " + serverName + ". Request timed out.", NamedTextColor.RED).sendMessage(source);
                enableActionBarPackets(player.getUniqueId());
                task.cancel();
                return;
            }
            switch (count) {//▇▆▅▃▂▂▂▂▂ ▆▇▆▅▃▂▂▂▂ ▅▆▇▆▅▃▂▂▂ ▃▅▆▇▆▅▃▂▂ ▂▃▅▆▇▆▅▃▂ ▂▂▃▅▆▇▆▅▃ ▂▂▂▃▅▆▇▆▅ ▂▂▂▂▃▅▆▇▆ ▂▂▂▂▂▃▅▆▇
                case 1 -> textComponent = Component.text("▇▆▅▃▂▂▂▂▂", NamedTextColor.GOLD);
                case 2 -> textComponent = Component.text("▆▇▆▅▃▂▂▂▂", NamedTextColor.GOLD);
                case 3, 15 -> textComponent = Component.text("▅▆▇▆▅▃▂▂▂", NamedTextColor.GOLD);
                case 4, 14 -> textComponent = Component.text("▃▅▆▇▆▅▃▂▂", NamedTextColor.GOLD);
                case 5, 13 -> textComponent = Component.text("▂▃▅▆▇▆▅▃▂", NamedTextColor.GOLD);
                case 6, 12 -> textComponent = Component.text("▂▂▃▅▆▇▆▅▃", NamedTextColor.GOLD);
                case 7, 11 -> textComponent = Component.text("▂▂▂▃▅▆▇▆▅", NamedTextColor.GOLD);
                case 8, 10 -> textComponent = Component.text("▂▂▂▂▃▅▆▇▆", NamedTextColor.GOLD);
                case 9 -> textComponent = Component.text("▂▂▂▂▂▃▅▆▇", NamedTextColor.GOLD);
                case 16 -> {
                    textComponent = Component.text("▆▇▆▅▃▂▂▂▂", NamedTextColor.GOLD);
                    count = 0;
                }
            }
            assert textComponent != null;
            sendSilentActionBarMessage(textComponent, player);
            //ensure player is still in queue for this server and hasn't changed
            if (!SLS.PLAYER_CONNECTOR.queue.get(uuid).equals(serverName)) {
                enableActionBarPackets(player.getUniqueId());
                task.cancel();//cancel this queue if player changed
                //if no other players are in queue for this server stop it from starting
                if (!SLS.PLAYER_CONNECTOR.queue.containsValue(serverName)) {
                    SLS.SERVER_REGISTRY.killServer(serverName);
                }
            }
        }).delay(0, TimeUnit.MILLISECONDS).repeat(70, TimeUnit.MILLISECONDS).schedule();
    }
}
