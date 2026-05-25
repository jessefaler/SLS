package net.slimelabs.vsls.utils;

import com.velocitypowered.api.plugin.PluginContainer;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.platform.ProtocolDetectorService;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;

import java.util.Optional;

public class ViaVersion {
    private static ProtocolDetectorService detector;

    /**
     * Registers a server with ViaVersion's protocol detection service.
     * <p>
     * If the proxy is running the ViaVersion plugin, each backend server must be
     * registered with ViaVersion immediately upon creation. This ensures ViaVersion
     * is aware of the server and can begin translating packets right away.
     * <p>
     * If a server is not registered immediately, players joining with a different
     * Minecraft version may receive an "Outdated server" error. This happens because
     * ViaVersion only pings servers at intervals (by default once every 60 seconds),
     * and ViaVersion does not know the server's protocol version until that ping occurs.
     * <p>
     * If the ViaVersion plugin is not present on the proxy this will do nothing and return
     *
     * @param server the server to register with ViaVersion
     */
    public static void register(Server server) {
        if (!isUsingViaVersion()) return;
        String version = resolveMinecraftVersion(server);
        if (version == null) {
            return;
        }
        ProtocolVersion protocolId = ProtocolVersion.getClosest(version);
        if (protocolId == null) {
            Log.error("failed to get protocol version for minecraft version {} while registering server {}", version, server.getCompositeId());
        } else {
            getDetector().setProtocolVersion(server.getCompositeId(), protocolId.getVersion());
        }
    }

    private static String resolveMinecraftVersion(Server server) {
        String version = server.getVersion();
        if (version == null || version.isEmpty() || "null".equals(version)) {
            version = server.getSoftwareVersion();
        }
        if (version == null || version.isEmpty() || "null".equals(version)) {
            return null;
        }
        return version;
    }

    /**
     * Unregisters a server with ViaVersion
     * <p>
     * If the ViaVersion plugin is not present on the proxy
     * this will do nothing and return
     * @param serverId the id of the server
     */
    public static void unregister(String serverId) {
        if(!isUsingViaVersion()) return; // ViaVersion is not in use on the proxy so return
        getDetector().uncacheProtocolVersion(serverId);
    }

    /**
     * Retrieves the shared ProtocolDetectorService instance.
     * If it has not been initialized yet a new instance will
     * be created
     *
     * @return the initialized ProtocolDetectorService instance
     */
    private static ProtocolDetectorService getDetector() {
        if (detector == null) {
            ViaVersion.detector = Via.proxyPlatform().protocolDetectorService();
        }
        return detector;
    }

    /**
     * Checks if the velocity server is using the viaversion plugin
     * @return true if the plugin container for viaversion is present
     */
    public static boolean isUsingViaVersion() {
        Optional<PluginContainer> container = SLS.proxy.getPluginManager().getPlugin("viaversion");
        return container.isPresent();
    }
}
