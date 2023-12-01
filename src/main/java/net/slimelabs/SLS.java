package net.slimelabs;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.util.UUID;

/* Server Management System <>
 * Author: protoxon
 * Network: SlimeLabs.net
 * Core plugin class for SLS
 * SLS - Slime Labs Server <>
 */
public final class SLS extends Plugin implements Listener {

    public static ProxyServer PROXY;
    public static MinigameRegistry MINIGAME_REGISTRY;
    public static FileHandler FILE_HANDLER;
    public static ServerRegistry SERVER_REGISTRY;
    public static PlayerConnector PLAYER_CONNECTOR;
    @Override
    public void onEnable() {
        PROXY = getProxy(); //sets this proxy to a global variable so other classes can access methods from proxy
        PROXY.getLogger().info("§3[SLS] §8SlimeLabs Server Management Plugin. Version 2.0 by protoxon");//Send Message To Console
        PROXY.getPluginManager().registerCommand(this, new Commands.sls());//registers the sls command
        MINIGAME_REGISTRY = new MinigameRegistry();//initialize the minigame registry class
        FILE_HANDLER = new FileHandler();//initialize the file handler class
        SERVER_REGISTRY = new ServerRegistry();//initialize the server registry class
        PLAYER_CONNECTOR = new PlayerConnector(this);//initialize the player connector class

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().registerChannel("slimelabs:network");
    }
    @Override
    public void onDisable() {
        PROXY.getLogger().info("§3[SLS] §8Proxy shutting down. Closing all minigame servers.");//Send Message To Console
        SLS.SERVER_REGISTRY.shutdownAllServers();//shutdown all minigame servers
    }

    //plugin messenger to receive messages from the spigot plugin that forwards the join command to the proxy
    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) throws Exception {
        if (event.getTag().equals("slimelabs:network") && !event.isCancelled()) {
            // Read the data from the message
            String resultString = new String(event.getData());
            String[] parts = resultString.split(";");
            UUID playerUUID = UUID.fromString(parts[0]);
            ProxiedPlayer targetPlayer = PROXY.getPlayer(playerUUID);
            PLAYER_CONNECTOR.joinServer(parts[1].replace("_", " ").trim(), targetPlayer);
            // Now you can process the receivedData as needed
        }
    }
}
