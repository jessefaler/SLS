package net.slimelabs.sls;

import net.slimelabs.sls.registries.RegistryManager;
import org.slf4j.Logger;
import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import static net.slimelabs.sls.utils.Color.*;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;

@Plugin(
        id = "sls",
        name = "SLS",
        version = "3.0.0",
        description = "Slimelabs Network",
        url = "slimelabs.net",
        authors = {"protoxon & Yeetoxic"}
)

/* Server Management System <>
 * Authors: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * Core plugin class for SLS
 * SLS - Slime Labs Server <>
 */
public class SLS {
    public static Logger LOGGER;
    public static ProxyServer PROXY;
    public static ServerRegistry SERVER_REGISTRY;
    public static RegistryManager REGISTRY_MANAGER;
    public static SLS PLUGIN;
    @Inject //injects the proxy server and logger into the plugin class (dependency injection)
    public SLS(ProxyServer PROXY, Logger LOGGER) {
        SLS.LOGGER = LOGGER;
        SLS.PROXY = PROXY;
        SLS.PLUGIN = this;
    }
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        LOGGER.info("\n" + CYAN + "————————————————————————————————————————————————\n" +
                GREEN + "  ___ _    ___ \n" +
                GREEN + " / __| |  / __|" + RED + " Server Launch System " + YELLOW + "v3.0.0" + "\n" +
                GREEN + " \\__ \\ |__\\__ \\" + DARK_GRAY + " Network Management Plugin" + "\n" +
                GREEN + " |___/____|___/" + LIGHT_BLUE + " Made by: " + MAGENTA + "Protoxon & Yeetoxic" + "\n" +
                RESET + "\n" + "[" + GREEN + "SLS" + RESET + "]" + LIGHT_BLUE + " Made for " + RESET + GREEN + "SlimeLabs.net"
                + RESET + LIGHT_BLUE + ", " + BLUE + "Established " + RESET + MAGENTA + "2013" + RESET + LIGHT_BLUE
                +  "!" + RESET + "\n" + " \n" + CYAN + "————————————————————————————————————————————————");

        //Register the sls command
        //CommandManager commandManager = PROXY.getCommandManager();
        //commandManager.register(commandManager.metaBuilder("sls").build(), new Commands.sls());
    }
    @Subscribe
    public void OnProxyShutdown(ProxyShutdownEvent event) {
        LOGGER.info("\n" + RED + "————————————————————————————————————————————————\n" +
                GREEN + "  ___ _    ___ \n" +
                GREEN + " / __| |  / __|" + DARK_GRAY + " Server Launch System " + "\n" +
                GREEN + " \\__ \\ |__\\__ \\" + RED + " Shutting Down..." + "\n" +
                GREEN + " |___/____|___/" + DARK_GRAY + " Made by: Protoxon & Yeetoxic" + "\n" +
                RESET +
                "\n" + RED + "————————————————————————————————————————————————");
    }
}

