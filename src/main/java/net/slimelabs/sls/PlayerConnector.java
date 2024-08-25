package net.slimelabs.sls;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.concurrent.TimeUnit;

/* Server Management System <>
 * Author: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * methods for sending players to servers in the Servers Registry
 * SLS - Slime Labs Network <>
 */
public class PlayerConnector extends ServerRegistry {

    private final SLS plugin;
    public Set<UUID> debugEnabledPlayers = new HashSet<>();
    public Map<UUID, String> playersInQueue = new HashMap<>();

    public PlayerConnector(SLS plugin) {
        this.plugin = plugin;
    }

    //connects a player to a server.
    //If the Server isn't already running it starts it and then connects the player
    //@param name the name of the server to join
    public void joinServer(String name, CommandSource sender) {
        if (!SLS.REGISTRY_ROUTER.containsWorld(name)) {//check if the name is a valid minigame in the minigame registry
            sender.sendMessage(Component.text("[§aSLS§r] No such world " + toTitleCase(name), NamedTextColor.DARK_RED));
            if (sender.hasPermission("sls.command.admin")) {
                sender.sendMessage(Component.text("[§aSLS§r] " + toTitleCase(name) + " was not found in any registry. Check the registry's config file", NamedTextColor.GRAY));
            }
            return;
        }
        if (SLS.SERVER_REGISTRY.SERVERS.containsKey(name)) {
            if (SLS.SERVER_REGISTRY.isServerOnline(name)) {//server is on and ready for players
                connectToServer(name, sender);
                return;
            }
            Player player = (Player) sender;
            if (SLS.PLAYER_CONNECTOR.playersInQueue != null && SLS.PLAYER_CONNECTOR.playersInQueue.get(player.getUniqueId()) != null && SLS.PLAYER_CONNECTOR.playersInQueue.get(player.getUniqueId()).equals(name)) {
                sender.sendMessage(Component.text("[§aSLS§r] You are already in queue for " + name, NamedTextColor.DARK_RED));
                return;
            }
            new Task(name, sender);
            return;
        }
        Player player = (Player) sender;
        if (SLS.PLAYER_CONNECTOR.playersInQueue != null && SLS.PLAYER_CONNECTOR.playersInQueue.get(player.getUniqueId()) != null && SLS.PLAYER_CONNECTOR.playersInQueue.get(player.getUniqueId()).equals(name)) {
            sender.sendMessage(Component.text("[§aSLS§r] You are already in queue for " + name, NamedTextColor.DARK_RED));
            return;
        }
        //start the server
        if (sender.hasPermission("sls.command.admin")) {
            String output = SLS.SERVER_REGISTRY.startServer(name);
            if (output != null) {//an issue occurred log the error message to a player
                sender.sendMessage(Component.text(output, NamedTextColor.DARK_RED));
                return;
            }
        } else {
            String output = SLS.SERVER_REGISTRY.startServer(name);
            if (output != null) {//an issue occurred log the error message to a player
                sender.sendMessage(Component.text("[§aSLS§r] Failed to join " + name, NamedTextColor.DARK_RED));
                return;
            }
        }
        new Task(name, sender);
    }

    //connects the player to a server if it is online
    public void connectToServer(String name, CommandSource sender) {
        if (!SLS.REGISTRY_ROUTER.containsWorld(name)) {//check if the name is a valid minigame in the minigame registry
            sender.sendMessage(Component.text("[§aSLS§r] Failed to connect to server. Server " + name + " does not exist.", NamedTextColor.DARK_RED));
            return;
        } else if (!SLS.SERVER_REGISTRY.SERVERS.containsKey(name)) {
            sender.sendMessage(Component.text("[§aSLS§r] Failed to connect to server. Server " + name + " is not online", NamedTextColor.DARK_RED));
            return;
        }
        Player player = (Player) sender;
        SLS.PROXY.getServer(name.trim().replace(" ", "_").toLowerCase()).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).fireAndForget(),
                () -> player.sendMessage(Component.text("[§aSLS§r] Server not found.", NamedTextColor.DARK_RED))
        );
    }

    //waits to connect a player to a server
    private class Task {
        private ScheduledTask task;
        private int count;
        private int max;

        public Task(String serverName, CommandSource sender) {
            Player player = (Player) sender;
            if (SLS.PLAYER_CONNECTOR.debugEnabledPlayers.contains(player.getUniqueId())) {
                queuePlayerToJoinServerDebugMode(serverName, sender);
                return;
            }
            queuePlayerToJoinServer(serverName, sender);
        }

        public void queuePlayerToJoinServer(String serverName, CommandSource sender) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            SLS.PLAYER_CONNECTOR.playersInQueue.put(uuid, serverName);
            sender.sendMessage(Component.text("[§aSLS§r] In queue for " + serverName, NamedTextColor.DARK_AQUA));

            task = plugin.PROXY.getScheduler().buildTask(plugin, () -> {
                if (!player.isActive()) {//player disconnected from the server while in queue.
                    SLS.PLAYER_CONNECTOR.playersInQueue.remove(uuid);//remove them from the players in queue map
                    //if no other players are in queue for this server stop it from starting
                    if (!SLS.PLAYER_CONNECTOR.playersInQueue.containsValue(serverName)) {
                        SLS.SERVER_REGISTRY.shutdownServer(serverName);
                    }
                    task.cancel();//cancel this task
                    SLS.LOGGER.info("Player disconnected while in queue.");
                    return;
                }
                Component textComponent = null;
                if (SLS.SERVER_REGISTRY.isServerOnline(serverName)) {
                    textComponent = Component.text("Joining " + serverName, NamedTextColor.GREEN);
                    player.sendActionBar(textComponent);
                    SLS.PLAYER_CONNECTOR.playersInQueue.remove(uuid);
                    SLS.PROXY.getServer(serverName.trim().replace(" ", "_").toLowerCase()).ifPresentOrElse(
                            targetServer -> player.createConnectionRequest(targetServer).fireAndForget(),
                            () -> player.sendMessage(Component.text("[§aSLS§r] Server not found.", NamedTextColor.DARK_RED)));
                    task.cancel();
                    return;
                }
                if (SLS.SERVER_REGISTRY.isShutdown(serverName)) {
                    sender.sendMessage(Component.text("[§aSLS§r] Failed to join " + serverName, NamedTextColor.DARK_RED));
                    SLS.PLAYER_CONNECTOR.playersInQueue.remove(uuid);
                    task.cancel();
                    return;
                }
                //show a loading animation in action bar
                count++;
                max++;
                if (max > 680) {
                    sender.sendMessage(Component.text("[§aSLS§r] Failed to join " + serverName, NamedTextColor.DARK_RED));
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
                player.sendActionBar(textComponent);
                //ensure player is still in queue for this server and hasn't changed
                if (!SLS.PLAYER_CONNECTOR.playersInQueue.get(uuid).equals(serverName)) {
                    task.cancel();//cancel this queue if player changed
                    //if no other players are in queue for this server stop it from starting
                    if (!SLS.PLAYER_CONNECTOR.playersInQueue.containsValue(serverName)) {
                        SLS.SERVER_REGISTRY.shutdownServer(serverName);
                    }
                }
            }).delay(0, TimeUnit.MILLISECONDS).repeat(70, TimeUnit.MILLISECONDS).schedule();
        }

        public void queuePlayerToJoinServerDebugMode(String serverName, CommandSource sender) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            SLS.PLAYER_CONNECTOR.playersInQueue.put(uuid, serverName);
            sender.sendMessage(Component.text("[§aSLS§r] Starting Server", NamedTextColor.DARK_AQUA));
            sender.sendMessage(Component.text("[§aSLS§r] Awaiting Response From Server", NamedTextColor.GRAY));

            task = plugin.PROXY.getScheduler().buildTask(plugin, () -> {
                if (!player.isActive()) {//player disconnected from the server while in queue.
                    SLS.PLAYER_CONNECTOR.playersInQueue.remove(uuid);//remove them from the players in queue map
                    //if no other players are in queue for this server stop it from starting
                    if (!SLS.PLAYER_CONNECTOR.playersInQueue.containsValue(serverName)) {
                        SLS.SERVER_REGISTRY.shutdownServer(serverName);
                    }
                    task.cancel();//cancel this task
                    return;
                }
                max++;
                if (max > 680) {
                    sender.sendMessage(Component.text("[§aSLS§r] Failed to join " + serverName, NamedTextColor.DARK_RED));
                    task.cancel();
                    return;
                }
                if (SLS.SERVER_REGISTRY.isServerOnline(serverName)) {
                    SLS.PLAYER_CONNECTOR.playersInQueue.remove(player.getUniqueId());
                    sender.sendMessage(Component.text("[§aSLS§r] Response Received. Sending you to " + serverName, NamedTextColor.GRAY));
                    SLS.PROXY.getServer(serverName.trim().replace(" ", "_").toLowerCase()).ifPresentOrElse(
                            targetServer -> player.createConnectionRequest(targetServer).fireAndForget(),
                            () -> player.sendMessage(Component.text("[§aSLS§r] Server not found.", NamedTextColor.DARK_RED))
                    );
                    task.cancel();
                }
                if (SLS.SERVER_REGISTRY.isShutdown(serverName)) {
                    SLS.PLAYER_CONNECTOR.playersInQueue.remove(player.getUniqueId());
                    sender.sendMessage(Component.text("[§aSLS§r] Failed to start server " + serverName, NamedTextColor.DARK_RED));
                    task.cancel();
                }
            }).delay(0, TimeUnit.MILLISECONDS).repeat(250, TimeUnit.MILLISECONDS).schedule();
        }
    }

    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input; // Return the input unchanged if it's null or empty
        }

        // Split the input string into words
        String[] words = input.split("\\s+");

        // Capitalize the first letter of each word
        for (int i = 0; i < words.length; i++) {
            words[i] = capitalize(words[i]);
        }

        // Join the words back together
        return String.join(" ", words);
    }

    //capitalize a word
    public static String capitalize(String word) {
        if (word == null || word.isEmpty()) {
            return word; // Return the word unchanged if it's null or empty
        }
        // Capitalize the first letter and append the rest of the word
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
