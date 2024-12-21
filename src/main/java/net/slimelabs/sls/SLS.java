package net.slimelabs.sls;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.google.inject.Inject;
import com.mojang.brigadier.CommandDispatcher;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.slimelabs.sls.command.SLSCommand;
import net.slimelabs.sls.io.FileHandler;
import net.slimelabs.sls.io.RegistryIO;
import net.slimelabs.sls.registries.RegistryManager;
import net.slimelabs.sls.server.ServerRegistry;
import net.slimelabs.sls.utils.PlayerUtils;
import org.slf4j.Logger;
import static net.slimelabs.sls.utils.Color.*;

/*
 * Copyright (C) 2024 Slimelabs Networks and contributors.
 *
 * SLS (Slimelabs Server) is server management software developed by the Slimelabs team and licensed under the MIT License.
 *
 * You should have received a copy of the MIT License along with this program.
 * If not, you can find it at <https://github.com/jessefaler/SLS/blob/main/LICENSE>.
 */

/* Server Management System <>
 * Authors: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * Core plugin class for SLS
 * SLS - Slime Labs Server <>
 */

@Plugin(
        id = "sls",
        name = "SLS",
        version = "1.0.0"
)
public class SLS {
    public static Logger LOGGER;
    public static ProxyServer PROXY;
    public static PacketListener PACKET_LISTENER;
    public static RegistryManager REGISTRY_MANAGER;
    public static ServerRegistry SERVER_REGISTRY;
    public static FileHandler FILE_HANDLER;
    public static RegistryIO REGISTRYIO;
    public static SLS PLUGIN;


    @Inject //injects the proxy server and logger into the plugin class (dependency injection)
    public SLS(ProxyServer PROXY, Logger LOGGER) {
        SLS.LOGGER = LOGGER;
        SLS.PROXY = PROXY;
        SLS.PLUGIN = this;
    }
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        LOGGER.info(startMessage());

        // Initialize classes
        REGISTRY_MANAGER = new RegistryManager();
        FILE_HANDLER = new FileHandler();
        REGISTRYIO = new RegistryIO();
        //DEBUGGER = new Debugger();
        REGISTRYIO.reloadAllRegistries(); // Load in the registries
        REGISTRYIO.printUnassignedWorlds(); // Print any unassigned worlds
        //PLAYER_CONNECTOR = new PlayerConnector();
        SERVER_REGISTRY = new ServerRegistry();
        PACKET_LISTENER = new PacketListener();

        // Register packet listeners
        PacketEvents.getAPI().getEventManager().registerListener(PACKET_LISTENER, PacketListenerPriority.NORMAL);
        PacketEvents.getAPI().init(); // Initialize PacketEvents API

        // Register the sls command
        SLSCommand.register();
    }

    @Subscribe
    public void OnProxyShutdown(ProxyShutdownEvent event) {
        LOGGER.info(shutdownMessage());
        //SERVER_REGISTRY.shutdownAllServers();
    }
    public String startMessage() {
        return "\n" + CYAN + "————————————————————————————————————————————————\n" +
                GREEN + "  ___ _    ___ \n" +
                GREEN + " / __| |  / __|" + RED + " Server Launch System " + YELLOW + "v3.0.0" + "\n" +
                GREEN + " \\__ \\ |__\\__ \\" + DARK_GRAY + " Network Management Plugin" + "\n" +
                GREEN + " |___/____|___/" + LIGHT_BLUE + " Made by: " + MAGENTA + "Protoxon & Yeetoxic" + "\n" +
                RESET + "\n" + "[" + GREEN + "SLS" + RESET + "]" + LIGHT_BLUE + " Made for " + RESET + GREEN + "SlimeLabs.net"
                + RESET + LIGHT_BLUE + ", " + BLUE + "Established " + RESET + MAGENTA + "2013" + RESET + LIGHT_BLUE
                +  "!" + RESET + "\n" + " \n" + CYAN + "————————————————————————————————————————————————" + RESET;
    }
    public String shutdownMessage() {
        return "\n" + RED + "————————————————————————————————————————————————\n" +
                GREEN + "  ___ _    ___ \n" +
                GREEN + " / __| |  / __|" + DARK_GRAY + " Server Launch System " + "\n" +
                GREEN + " \\__ \\ |__\\__ \\" + RED + " Shutting Down..." + "\n" +
                GREEN + " |___/____|___/" + DARK_GRAY + " Made by: Protoxon & Yeetoxic" + "\n" +
                RESET +
                "\n" + RED + "————————————————————————————————————————————————" + RESET;
    }
}
