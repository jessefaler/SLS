package net.slimelabs.sls;


import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.server.core.ServerInstance;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.StringUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/* Server Management System <>
 * Author: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * Handles queueing and sending players to servers
 * SLS - Slime Labs Network <>
 */
public class PlayerConnector {

    // Contain players who are in queue for a server
    // players UUID --> server name
    public Map<UUID, String> queue = new ConcurrentHashMap<>();

    public void joinServer(String name, String registry, CommandSource source) {
        if(SLS.SERVER_REGISTRY.containsServer(name)) {
            if(SLS.SERVER_REGISTRY.isOnline(name)) {//server is on and ready for players
                connectToServer(name, source);
                return;
            }
            // Server is in the registry and is not running and not stopping so assume it is starting
            // Queue the player to join the server once it is running
            if(!SLS.SERVER_REGISTRY.isStopping(name)) {
                Player player = (Player) source;
                if(SLS.PLAYER_CONNECTOR.queue != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()) != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()).equals(name)) {
                    Message.chat().add(MessagePreset.SLS).add(" You are already in queue for " + name, NamedTextColor.RED);
                    return;
                }
                new QueueService().queuePlayerToJoinServer(name, source); // Queue the player
                return;
            }
        }
        String worldName = StringUtils.removeTextAfterPeriod(name); // Removes the namespace
        if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registry)) { // Registry does not exist send error message
            Message.chat().add(MessagePreset.SLS).add(" No such registry " + registry, NamedTextColor.RED);
            return;
        }
        if(!SLS.REGISTRY_MANAGER.getRegistry(registry).isWorldPresent(worldName)) { // World does not exist in this registry send error message
            if(SLS.REGISTRY_MANAGER.doseWorldExist(worldName)) {// World exists in a registry but not in the given one
                Message.chat().add(MessagePreset.SLS).add(worldName + " dose not exist in the " + registry + " registry", NamedTextColor.RED);
                return;
            }
            // No world exists with the given name send error message
            Message.chat().add(MessagePreset.SLS).add(" No such world " + worldName, NamedTextColor.RED);
            return;
        }
        // The registry is valid and the world exists but the server has not yet been started so start it and queue the player if they are not already queued
        Player player = (Player) source;
        if(SLS.PLAYER_CONNECTOR.queue != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()) != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()).equals(name)) {
            Message.chat().add(MessagePreset.SLS).add(" You are already in queue for " + name, NamedTextColor.RED);
            return;
        }
        ServerConfiguration serverConfiguration = SLS.REGISTRY_MANAGER.getRegistry(registry).getWorld(worldName);
        // Execute the server start on a separate thread so Http requests don't block the main thread.
        SLS.EXECUTOR.submit(() -> {
            boolean success = SLS.SERVER_REGISTRY.startServer(name, serverConfiguration);
            if(!success) {
                Message.chat()
                        .add("An error occurred while attempting to start the server. Please try again or check the logs for more details.", NamedTextColor.RED)
                        .sendMessage(source);
            }
        });
        new QueueService().queuePlayerToJoinServer(name, source); // Queue the player
    }

    // Connects the player to a server if it is online
    public void connectToServer(String name, CommandSource source) {
        Player player = (Player) source;
        SLS.PROXY.getServer(name).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).fireAndForget(),
                () -> Message.chat().add(MessagePreset.SLS).add(" Server not found.", NamedTextColor.RED).sendMessage(source));
    }
}
