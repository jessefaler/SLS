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
import net.slimelabs.vsls.command.SLSCommand;
import net.slimelabs.vsls.config.Config;
import net.slimelabs.vsls.internal.Message;
import net.slimelabs.vsls.packets.ChatPackets;
import net.slimelabs.vsls.routing.AnimationController;
import net.slimelabs.vsls.routing.QueueManager;
import net.slimelabs.vsls.server.ServerManager;
import org.slf4j.Logger;

@Plugin(
        id = "vsls",
        name = "vSLS",
        version = "1.0.0",
        description = "Server Management Plugin",
        authors = {"Protoxon & Contributors"},
        dependencies = {
                @Dependency(id = "packetevents"),
                @Dependency(id = "viaversion", optional = true)
        }
)
public class SLS {

    public static ProxyServer       proxy;
    public static SLS               plugin;
    public static ServerManager servers;
    public static BlueprintRegistry blueprints;
    public static Config            config;
    public static SLSClient         api;
    public static ChatPackets       chatPackets;
    public static QueueManager      queue;

    //todo implement database
    // Use the Hibernate library to abstract database logic
    // and have a choice in the config to use either a sqlite database or a sql server (MySQL, PostgreSQL, ect)

    @Inject // injects the proxy server and logger into the plugin class
    public SLS(ProxyServer proxy, Logger logger) {
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
        api = SLSBuilder.createClient(config.api.url, config.api.key);
        // Initialize the blueprint registry
        blueprints = BlueprintRegistry.init();
        // Initialize the server registry
        servers = ServerManager.init(api);
        // Initialize the packet listener
        chatPackets = ChatPackets.init();
        // Register the sls command
        SLSCommand.register();
        proxy.getEventManager().register(this, new AnimationController());
        SLS.queue = new QueueManager();
    }

    @Subscribe
    public void OnProxyShutdown(ProxyShutdownEvent event) {
        // Close the event stream
        SLS.servers.events.stop();
    }

}
