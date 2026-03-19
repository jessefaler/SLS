package net.slimelabs.vsls;

import com.google.inject.Inject;
import com.protoxon.S4J.SLSBuilder;
import com.protoxon.S4J.client.entities.SLSClient;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.slimelabs.vsls.blueprints.BlueprintRegistry;
import net.slimelabs.vsls.channel.MessageChannel;
import net.slimelabs.vsls.command.SLSCommand;
import net.slimelabs.vsls.config.Config;
import net.slimelabs.vsls.events.EventRouter;
import net.slimelabs.vsls.events.EventStream;
import net.slimelabs.vsls.internal.Message;
import net.slimelabs.vsls.matchmaking.MatchmakingManager;
import net.slimelabs.vsls.matchmaking.join.DirectServerJoiner;
import net.slimelabs.vsls.matchmaking.join.JoinService;
import net.slimelabs.vsls.matchmaking.registry.GameTypeRegistry;
import net.slimelabs.vsls.matchmaking.strategies.RandomBlueprintStrategy;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.server.ServerManager;

import static net.slimelabs.vsls.channel.MessageChannel.SLS_CHANNEL;

@Plugin(
        id = "vsls",
        name = "vSLS",
        version = "1.0.0",
        description = "Server Management Plugin",
        authors = {"Protoxon & Contributors"},
        dependencies = {
                // PacketEvents is used by ChatPackets for actionbar control
                @Dependency(id = "packetevents"),
                // ViaVersion is used to register servers
                // with Via's protocol detection service
                @Dependency(id = "viaversion", optional = true)
        }
)

public class SLS {

    public static ProxyServer       proxy;
    public static SLS               plugin;
    public static Config            config;

    public static ServerManager     servers;
    public static BlueprintRegistry blueprints;
    public static SLSClient         api;

    public static GameTypeRegistry   gameTypes;
    public static MatchmakingManager matchmaking;
    public static JoinService        joinService;

    private static EventStream eventStream;

    // todo implement database
    // Use the Hibernate library to abstract database logic
    // and have a choice in the config to use either a sqlite database or a sql server (MySQL, PostgreSQL, ect)

    @Inject // injects the proxy server into the plugin class
    public SLS(ProxyServer proxy) {
        SLS.proxy = proxy;
        SLS.plugin = this;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Display plugin banner
        Message.Banner();
        // Initialize the config
        config = Config.initConfig();
        // Create the S4J api client
        SLSClient api = SLSBuilder.createClient(config.api.url, config.api.key);
        // Initialize the blueprint registry
        BlueprintRegistry blueprintRegistry = new BlueprintRegistry(api);
        SLS.blueprints = blueprintRegistry;
        // Initialize the event stream
        EventStream eventStream = new EventStream(api.getEventStream());
        SLS.eventStream = eventStream;
        // Start the event stream
        eventStream.start();
        // Initialize the event router
        EventRouter eventRouter = new EventRouter(eventStream);
        // Initialize the server registry
        ServerManager serverManager = new ServerManager(api, eventRouter);
        SLS.servers = serverManager;
        // Initialize the packet listener
        ChatPackets.init();
        // Register the sls command
        SLSCommand.register();
        // Game types from blueprint matchmaking metadata
        GameTypeRegistry gameTypeRegistry = new GameTypeRegistry();
        SLS.gameTypes = gameTypeRegistry;
        blueprintRegistry.whenLoaded(reg -> gameTypeRegistry.load(reg.getAll()));
        // Matchmaking and join entry point
        MatchmakingManager matchmakingManager = new MatchmakingManager(
                gameTypeRegistry,
                blueprintRegistry,
                new RandomBlueprintStrategy()
        );
        SLS.matchmaking = matchmakingManager;
        DirectServerJoiner directJoiner = new DirectServerJoiner();
        SLS.joinService = new JoinService(matchmakingManager, directJoiner);
        SLS.api = api;

        // Register the slimelabs network channel
        SLS.proxy.getChannelRegistrar().register(SLS_CHANNEL);
        // Register the plugin message listener
        SLS.proxy.getEventManager().register(this, new MessageChannel.SLSMessageListener());
    }

    @Subscribe
    public void OnProxyShutdown(ProxyShutdownEvent event) {
        // Close the event stream
        SLS.eventStream.stop();
    }

}
