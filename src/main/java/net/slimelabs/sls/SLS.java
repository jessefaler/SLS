package net.slimelabs.sls;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.slimelabs.sls.registries.AdventureMapRegistry;
import net.slimelabs.sls.registries.MinigameRegistry;
import net.slimelabs.sls.registries.RegistryRouter;
import net.slimelabs.sls.registries.TheArchiveRegistry;
import org.slf4j.Logger;

import java.util.UUID;

@Plugin(
        id = "sls",
        name = "SLS",
        version = "2.1.2",
        description = "Slimelabs Network",
        url = "slimelabs.net",
        authors = {"protoxon & Yeetoxic"}
)

/* Server Management System <>
 * Authors: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * Core plugin class for SLS
 * SLS - Slime Labs Network <>
 */
public class SLS {

    public static Logger LOGGER;
    public static ProxyServer PROXY;
    public static MinigameRegistry MINIGAME_REGISTRY;
    public static TheArchiveRegistry ARCHIVE_REGISTRY;
    public static AdventureMapRegistry ADVENTURE_REGISTRY;
    public static FileHandler FILE_HANDLER;
    public static ServerRegistry SERVER_REGISTRY;
    public static PlayerConnector PLAYER_CONNECTOR;
    public static RegistryRouter REGISTRY_ROUTER;
    public static SLS PLUGIN;

    private static final ChannelIdentifier SLS_CHANNEL = MinecraftChannelIdentifier.create("slimelabs", "network");

    @Inject //injects the proxy server and logger into the plugin class
    public SLS(ProxyServer PROXY, Logger LOGGER) {
        SLS.LOGGER = LOGGER;
        SLS.PROXY = PROXY;
        SLS.PLUGIN = this;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        String RESET = "\u001B[0m";
        String YELLOW = "\u001B[33m";
        String GREEN = "\u001B[32m";
        String CYAN = "\u001B[36m";
        String BLUE = "\u001B[34m";
        String RED = "\u001B[31m";       // Red
        String DARK_GRAY = "\u001B[90m"; // Dark Gray
        String MAGENTA = "\u001B[35m";   // Magenta (Purple)
        String LIGHT_BLUE = "\u001B[94m"; // Light Blue

        LOGGER.info(" \n" + CYAN + "————————————————————————————————————————————————\n" +
                GREEN + "  ___ _    ___ \n" +
                GREEN + " / __| |  / __|" + RED + " Server Launch System " + YELLOW + "v2.1.2" + "\n" +
                GREEN + " \\__ \\ |__\\__ \\" + DARK_GRAY + " Network Management Plugin" + "\n" +
                GREEN + " |___/____|___/" + LIGHT_BLUE + " Made by: " + MAGENTA + "Protoxon & Yeetoxic" + "\n" +
                RESET +
                "\n" +
                "[" + GREEN + "SLS" + RESET + "]" + LIGHT_BLUE + " Made for " + RESET + GREEN + "SlimeLabs.net" + RESET + LIGHT_BLUE + ", " + BLUE + "Established " + RESET + MAGENTA + "2013" + RESET + LIGHT_BLUE +  "!" + RESET + "\n" +
                " \n" + CYAN + "————————————————————————————————————————————————");

        // Register the example command
        CommandManager commandManager = PROXY.getCommandManager();
        commandManager.register(commandManager.metaBuilder("sls").build(), new Commands.sls());

        MINIGAME_REGISTRY = new MinigameRegistry();//initialize the minigame registry class
        ARCHIVE_REGISTRY = new TheArchiveRegistry();//initialize the archive registry class
        ADVENTURE_REGISTRY = new AdventureMapRegistry();//initialize the adventure registry class
        FILE_HANDLER = new FileHandler();//initialize the file handler class
        SERVER_REGISTRY = new ServerRegistry();//initialize the server registry class
        PLAYER_CONNECTOR = new PlayerConnector(this);//initialize the player connector class
        REGISTRY_ROUTER = new RegistryRouter();//initialize the registry router class

        // Register the slimelabs network channel
        PROXY.getChannelRegistrar().register(SLS_CHANNEL);
        // Register the plugin message listener
        PROXY.getEventManager().register(this, new SLSMessageListener());
    }

    public static class SLSMessageListener {
        @Subscribe
        public void onPluginMessage(PluginMessageEvent event) {
            if (!event.getIdentifier().equals(SLS_CHANNEL)) {
                return;
            }

            String resultString = new String(event.getData());
            String[] parts = resultString.split(";");
            UUID playerUUID = UUID.fromString(parts[0]);

            Player targetPlayer = PROXY.getPlayer(playerUUID).get();

            PLAYER_CONNECTOR.joinServer(parts[1].replace("_", " ").trim(), targetPlayer);
        }
    }

    @Subscribe
    public void OnProxyShutdown(ProxyShutdownEvent event) {
        String RESET = "\u001B[0m";
        String GREEN = "\u001B[32m";
        String RED = "\u001B[31m";       // Red
        String DARK_GRAY = "\u001B[90m"; // Dark Gray

        LOGGER.info(" \n" + RED + "————————————————————————————————————————————————\n" +
                GREEN + "  ___ _    ___ \n" +
                GREEN + " / __| |  / __|" + DARK_GRAY + " Server Launch System " + "\n" +
                GREEN + " \\__ \\ |__\\__ \\" + RED + " Shutting Down..." + "\n" +
                GREEN + " |___/____|___/" + DARK_GRAY + " Made by: Protoxon & Yeetoxic" + "\n" +
                RESET +
                "\n" + RED + "————————————————————————————————————————————————");
    }
}

