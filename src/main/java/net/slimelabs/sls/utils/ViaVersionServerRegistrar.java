package net.slimelabs.sls.utils;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.platform.ProtocolDetectorService;
import com.viaversion.viaversion.api.platform.ViaServerProxyPlatform;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

/**
 * Registers / Unregisters servers with ViaVersion
 */
public class ViaVersionServerRegistrar {

    private static final ViaServerProxyPlatform<?> platform = Via.proxyPlatform();
    private static final ProtocolDetectorService detector = platform.protocolDetectorService();

    /**
     * Registers a server with ViaVersion
     * @param serverName the name of the server
     * @param protocolId the protocol version of the server
     */
    public static void registerServer(String serverName, int protocolId) {
        //int protocolId = ProtocolVersion.v1_19_3.getVersion();  // for example, 1.19.3 -> 759
        detector.setProtocolVersion(serverName, protocolId);
    }

    /**
     * Unregisters a server with ViaVersion
     * @param serverName the name of the server
     */
    public static void unregisterServer(String serverName) {
        detector.uncacheProtocolVersion(serverName);
    }
}
