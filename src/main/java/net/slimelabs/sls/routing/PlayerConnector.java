package net.slimelabs.sls.routing;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.server.core.ServerInstance;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.slimelabs.sls.PacketListener.enableActionBarPackets;

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
    public Map<UUID, QueueService> activeServices = new ConcurrentHashMap<>();

    public void joinServer(String name, String registry, CommandSource source) {
        ServerInstance serverInstance;
        if(SLS.SERVER_REGISTRY.containsServer(name)) {
            serverInstance = SLS.SERVER_REGISTRY.getServer(name);
            if(serverInstance.state == UtilizationState.RUNNING) {//server is on and ready for players
                sendConnectionRequest((Player) source, name); // Send connection request
                return;
            }
            // The Server is in the registry and is not running and not stopping, so assume it is starting
            // to Queue the player to join the server once it is running
            if(serverInstance.state != UtilizationState.STOPPING) {
                Player player = (Player) source;
                if(SLS.PLAYER_CONNECTOR.queue != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()) != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()).equals(name)) {
                    ProtoMessage.chat().add(MessagePreset.SLS).add(" You are already in queue for " + name, NamedTextColor.RED);
                    return;
                }
                new QueueService().queuePlayerToJoinServer(name, SLS.SERVER_REGISTRY.getServer(name), source); // Queue the player
                return;
            }
            return;
        }
        serverInstance = new ServerInstance(name);
        String worldName = StringUtils.truncateAtPeriod(name); // Removes the namespace
        if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registry)) { // Registry does not exist send an error message
            ProtoMessage.chat().add(MessagePreset.SLS).add(" No such registry " + registry, NamedTextColor.RED);
            return;
        }
        if(!SLS.REGISTRY_MANAGER.getRegistry(registry).isWorldPresent(worldName)) { // World does not exist in this registry send error message
            if(SLS.REGISTRY_MANAGER.doseWorldExist(worldName)) {// World exists in a registry but not in the given one
                ProtoMessage.chat().add(MessagePreset.SLS).add(worldName + " dose not exist in the " + registry + " registry", NamedTextColor.RED);
                return;
            }
            // No world exists with the given name send error message
            ProtoMessage.chat().add(MessagePreset.SLS).add(" No such world " + worldName, NamedTextColor.RED);
            return;
        }
        // The registry is valid and the world exists but the server has not yet been started so start it and queue the player if they are not already queued
        Player player = (Player) source;
        if(SLS.PLAYER_CONNECTOR.queue != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()) != null && SLS.PLAYER_CONNECTOR.queue.get(player.getUniqueId()).equals(name)) {
            ProtoMessage.chat().add(MessagePreset.SLS).add(" You are already in queue for " + name, NamedTextColor.RED);
            return;
        }
        ServerConfiguration serverConfiguration = SLS.REGISTRY_MANAGER.getRegistry(registry).getWorld(worldName);
        // Execute the server start on a separate thread so Http requests don't block the main thread.
        SLS.EXECUTOR.submit(() -> {
            boolean success = SLS.SERVER_REGISTRY.startServer(name, serverConfiguration, null, serverInstance);
            if(!success) {
                ProtoMessage.chat()
                        .add("An error occurred while attempting to start the server. Please try again or check the logs for more details.", NamedTextColor.RED)
                        .sendMessage(source);
            }
        });
        new QueueService().queuePlayerToJoinServer(name, serverInstance, source); // Queue the player
    }

    public void dequeuePlayer(UUID uuid) {
        if(activeServices.containsKey(uuid)) {
            activeServices.get(uuid).dequeue();
        }
    }

    /**
     * Sends a connection request to join the given player to the specified server
     * @param player the player instance to connect
     * @param serverName the name of the server
     */
    public void sendConnectionRequest(Player player, String serverName) {
        SLS.PROXY.getServer(serverName).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).connectWithIndication().thenAccept(connection -> {
                    enableActionBarPackets(player.getUniqueId());
                    ProtoMessage.actionBar().sendMessage(player); // Send a blank message to clear their actionbar
                }).exceptionally(throwable -> {
                    // Handle connection failure
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Error: Could not connect to " + serverName, NamedTextColor.RED)
                            .sendMessage(player);
                    SLS.LOGGER.error("There was an error while connecting {} to {} ensure the ip and port are configured correctly and the server is accessible through velocity", player.getUsername(), serverName);
                    System.err.println(throwable.getMessage());
                    return null;
                }),
                () -> ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("Error: Server not found", NamedTextColor.RED)
                        .sendMessage(player)
        );
    }
}
