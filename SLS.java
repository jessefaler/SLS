package net.slimelabs;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import java.io.IOException;

/* Server Management System <>
 * Author: protoxon
 * Network: SlimeLabs.net
 * Core plugin class for SLS
 * SLS - Slime Labs Server <>
 */
public final class SLS extends Plugin {

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
    }
    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
