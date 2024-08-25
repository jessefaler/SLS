package net.slimelabs.sls;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
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
        version = "2.1.1HF",
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
        LOGGER.info(CYAN + " \n" + CYAN + "————————————————————————————————————————————————\n" +
                GREEN + "  ____  _ _                _          _         \n" +
                GREEN + " / ___|| (_)_ __ ___   ___| |    __ _| |__  ___ \n" +
                GREEN + " \\___ \\| | | '_ ` _ \\ / _ \\ |   / _` | '_ \\/ __|\n" +
                GREEN + "  ___) | | | | | | | |  __/ |__| (_| | |_) \\__ \\\n" +
                GREEN + " |____/|_|_|_| |_| |_|\\___|_____\\__,_|_.__/|___/\n" +
                GREEN + "  _   _ _____ _______        _____  ____  _  __ \n" +
                GREEN + " | \\ | | ____|_   _\\ \\      / / _ \\|  _ \\| |/ / \n" +
                GREEN + " |  \\| |  _|   | |  \\ \\ /\\ / / | | | |_) | ' /  \n" +
                GREEN + " | |\\  | |___  | |   \\ V  V /| |_| |  _ <| . \\  \n" +
                GREEN + " |_| \\_|_____| |_|    \\_/\\_/  \\___/|_| \\_\\_|\\_\\ \n" + RESET +
                "\n" +
                "[" + GREEN + "SLS" + RESET + "]" + CYAN + " SlimeLabs Server Management Plugin " + RESET + YELLOW + "Version 2.1.1HF" + RESET + CYAN +" by " + RESET + BLUE + "protoxon" + RESET + CYAN + " & " + RESET + BLUE + "Yeetoxic" + RESET + "\n" +
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
}

