package net.slimelabs.sls;


import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.StringUtils;

/* Server Management System <>
 * Author: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * Handles queueing and sending players to servers
 * SLS - Slime Labs Network <>
 */
public class PlayerConnector {

    public void joinServer(String name, String registry, Player player) {
        if(SLS.SERVER_REGISTRY.containsServer(name)) {
            if(SLS.SERVER_REGISTRY.isOnline(name)) {//server is on and ready for players
                connectToServer(name, player);
                return;
            }
            // Server is in the registry and is not running and not stopping so assume it is starting
            // Queue the player to join the server once it is running
            if(!SLS.SERVER_REGISTRY.isStopping(name)) {
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
        // The registry is valid and the world exists but is not yet running so start it and queue the player
        ServerConfiguration serverConfiguration = SLS.REGISTRY_MANAGER.getRegistry(registry).getWorld(worldName);
        SLS.SERVER_REGISTRY.startServer(name, serverConfiguration);
        Message.chat()
                .add(MessagePreset.SLS)
                .addMiniMessage("<gradient:#50CEC3:#005D3C>In queue for " + worldName.replace("_", " ") + ".</gradient>");
    }

    // Connects the player to a server if it is online
    public void connectToServer(String name, CommandSource source) {
        Player player = (Player) source;
        SLS.PROXY.getServer(name).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).fireAndForget(),
                () -> Message.chat().add(MessagePreset.SLS).add(" Server not found.", NamedTextColor.RED).sendMessage(source));
    }


}
