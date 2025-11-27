package net.slimelabs.vsls;

import com.google.inject.Inject;
import com.protoxon.S4J.SLSBuilder;
import com.protoxon.S4J.client.entites.SLSClient;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.slimelabs.vsls.blueprints.BlueprintRegistry;
import net.slimelabs.vsls.command.SLSCommand;
import net.slimelabs.vsls.config.Config;
import net.slimelabs.vsls.internal.Message;
import net.slimelabs.vsls.server.ServerRegistry;
import org.slf4j.Logger;

@Plugin(
        id = "vsls",
        name = "vSLS",
        version = "1.0.0",
        description = "Server Management Plugin",
        authors = {"Protoxon & Contributors"}
)
public class SLS {

    public static Logger            logger;
    public static ProxyServer       proxy;
    public static SLS               plugin;
    public static ServerRegistry    servers;
    public static BlueprintRegistry blueprints;
    public static Config            config;
    public static SLSClient         api;

    @Inject // injects the proxy server and logger into the plugin class
    public SLS(ProxyServer proxy, Logger logger) {
        SLS.logger = logger;
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
        servers = ServerRegistry.init(api);
        // Register the sls command
        SLSCommand.register();
    }

}
