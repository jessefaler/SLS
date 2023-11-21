package net.slimelabs;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.*;
import java.util.concurrent.TimeUnit;

/* Server Management System <>
 * Author: protoxon
 * Network: SlimeLabs.net
 * methods for sending players to servers in the Servers Registry
 * SLS - Slime Labs Server <>
 */
public class PlayerConnector extends ServerRegistry {

    public Set<UUID> debugEnabledPlayers = new HashSet<>();

    public Map<UUID, String> playersInQueue = new HashMap<>();
    Plugin plugin;
    public PlayerConnector(Plugin plugin) {
        this.plugin = plugin;
    }

    //connects a player to a server.
    //If the Server isn't already running it starts it and then connects the player
    //@param name the name of the server to join
    public void joinServer(String name, CommandSender sender) {
        for(String key : SLS.MINIGAME_REGISTRY.MinigameRegistry.keySet()) {//get the correct casing of the key
            if(key.equalsIgnoreCase(name)) {
                name = key;
                break;
            }
        }
        if(!SLS.MINIGAME_REGISTRY.containsMinigame(name)) {//check if the name is a valid minigame in the minigame registry
            sender.sendMessage("§cFailed to connect to server. Server " + name + " dose not exist.");
            return;
        }
        if(SLS.SERVER_REGISTRY.SERVERS.containsKey(name)) {
            if(SLS.SERVER_REGISTRY.isServerOnline(name)) {//server is on and ready for players
                connectToServer(name, sender);
                return;
            }
            new Task(name, sender, plugin);
            return;
        }
        //start the server
        SLS.SERVER_REGISTRY.startServer(SLS.MINIGAME_REGISTRY.getFolderName(name), SLS.MINIGAME_REGISTRY.getCustomRam(name), name);
        new Task(name, sender, plugin);

    }

    //connects the player to a server if it is online
    public void connectToServer(String name, CommandSender sender) {
        if(!SLS.MINIGAME_REGISTRY.containsMinigame(name)) {//check if the name is a valid minigame in the minigame registry
            sender.sendMessage("§cFailed to connect to server. Server " + name + " dose not exist.");
            return;
        } else if(!SLS.SERVER_REGISTRY.SERVERS.containsKey(name)) {
            sender.sendMessage("§cFailed to connect to server. Server " + name + " is not online");
            return;
        }
        ProxiedPlayer player = (ProxiedPlayer) sender;
        ServerInfo serverToConnectTo = SLS.PROXY.getServerInfo(name.trim().replace(" ", "_").toLowerCase());
        player.connect(serverToConnectTo);
    }


    //waits to connect a player to a server
    private static class Task {
        private ScheduledTask task;
        private int count;

        private int max;
        public Task(String serverName, CommandSender sender, Plugin plugin) {
            ProxiedPlayer player = (ProxiedPlayer) sender;
            if(SLS.PLAYER_CONNECTOR.debugEnabledPlayers.contains(player.getUniqueId())) {
                queuePlayerToJoinServerDebugMode(serverName, sender, plugin);
                return;
            }
            queuePlayerToJoinServer(serverName, sender, plugin);
        }
        public void queuePlayerToJoinServer(String serverName, CommandSender sender, Plugin plugin) {
            ProxiedPlayer player = (ProxiedPlayer) sender;
            SLS.PLAYER_CONNECTOR.playersInQueue.put(player.getUniqueId(), serverName);
            ServerInfo serverInfo = SLS.PROXY.getServerInfo(serverName.trim().replace(" ", "_").toLowerCase());
            //this runs it on a separate thread. Part of bungee-cord scheduler
            task = SLS.PROXY.getScheduler().schedule(plugin, new Runnable() {
                @Override
                public void run() {
                    TextComponent textComponent = null;
                    if(SLS.SERVER_REGISTRY.isServerOnline(serverName)) {
                        textComponent = new TextComponent("§aJoining " + serverName);
                        player.sendMessage(ChatMessageType.ACTION_BAR, textComponent);
                        SLS.PLAYER_CONNECTOR.playersInQueue.remove(player.getUniqueId());
                        player.connect(serverInfo);
                        task.cancel();
                        return;
                    }
                    if(SLS.SERVER_REGISTRY.isShutdown(serverName)) {
                        sender.sendMessage("§cFailed to join " + serverName);
                        SLS.PLAYER_CONNECTOR.playersInQueue.remove(player.getUniqueId());
                        task.cancel();
                        return;
                    }
                    //show a loading animation in action bar
                    count++;
                    max++;
                    if(max > 180) {
                        sender.sendMessage("§cFailed to join " + serverName);
                        task.cancel();
                        return;
                    }
                    switch (count) {
                        case 1:
                            textComponent = new TextComponent(ChatColor.translateAlternateColorCodes(
                                    '&', ChatColor.GRAY + "In queue for "
                                            + serverName + ""));
                            break;
                        case 2:
                            textComponent = new TextComponent(ChatColor.translateAlternateColorCodes(
                                    '&', ChatColor.GRAY + "< In queue for "
                                            + serverName + " >"));
                            break;
                        case 3:
                            textComponent = new TextComponent(ChatColor.translateAlternateColorCodes(
                                    '&', ChatColor.GRAY + "<< In queue for "
                                            + serverName + " >>"));
                            break;
                        case 4:
                            textComponent = new TextComponent(ChatColor.translateAlternateColorCodes(
                                    '&', ChatColor.GRAY + "<<< In queue for "
                                            + serverName + " >>>"));
                            count = 0;
                            break;
                    }
                    player.sendMessage(ChatMessageType.ACTION_BAR, textComponent);
                    //ensure player is still in queue for this server and hasn't changed
                    if (!SLS.PLAYER_CONNECTOR.playersInQueue.get(player.getUniqueId()).equals(serverName)) {
                        task.cancel();//cancel this queue if player changed
                        //if no other players are in queue for this server stop it from starting
                        if(!SLS.PLAYER_CONNECTOR.playersInQueue.containsValue(serverName)) {
                            SLS.SERVER_REGISTRY.shutdownServer(serverName);
                        }
                    }
                }
            }, 250, 250, TimeUnit.MILLISECONDS);//period = how often to check if server is online
        }


        public void queuePlayerToJoinServerDebugMode(String serverName, CommandSender sender, Plugin plugin) {
            ProxiedPlayer player = (ProxiedPlayer) sender;
            SLS.PLAYER_CONNECTOR.playersInQueue.put(player.getUniqueId(), serverName);
            sender.sendMessage("§7Starting Server");
            sender.sendMessage("§7Awaiting Response From Server");
            ServerInfo serverInfo = SLS.PROXY.getServerInfo(serverName.trim().replace(" ", "_").toLowerCase());
            //this runs it on a separate thread. Part of bungee-cord scheduler
            task = SLS.PROXY.getScheduler().schedule(plugin, new Runnable() {
                @Override
                public void run() {
                    max++;
                    if(max > 180) {
                        sender.sendMessage("§cFailed to join " + serverName);
                        task.cancel();
                        return;
                    }
                    if(SLS.SERVER_REGISTRY.isServerOnline(serverName)) {
                        SLS.PLAYER_CONNECTOR.playersInQueue.remove(player.getUniqueId());
                        sender.sendMessage("§7Response Received. Sending you to " + serverName);
                        player.connect(serverInfo);
                        task.cancel();
                    }
                    if(SLS.SERVER_REGISTRY.isShutdown(serverName)) {
                        SLS.PLAYER_CONNECTOR.playersInQueue.remove(player.getUniqueId());
                        sender.sendMessage("§cFailed to start server " + serverName);
                        task.cancel();
                    }
                }
            }, 250, 250, TimeUnit.MILLISECONDS);//period = how often to check if server is online
        }
    }
}
