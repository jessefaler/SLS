package net.slimelabs.sls.server.core;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the payload data used in a HTTP POST request to configure a server.
 * This class includes properties such as server configuration, limits, feature limits,
 * environment variables, and Docker image setup for server deployment.
 */
public class Payload {
    String name = "test";
    int user = 1;  // User ID
    int egg = 2;   // Egg ID
    String docker_image = "ghcr.io/pterodactyl/yolks:java_21";
    String startup = "java -Xms128M -Xmx1024M -jar server.jar nogui";
    Limits limits = new Limits();
    FeatureLimits feature_limits = new FeatureLimits();
    Allocation allocation = new Allocation();
    Map<String, String> environment = new HashMap<>();

    public Payload() {
        environment.put("SERVER_JARFILE", "server.jar");
        environment.put("BUILD_NUMBER", "1");
    }

    static class Limits {
        int memory = 0;
        int swap = 0;
        int disk = 0;
        int io = 500;
        int cpu = 0;
    }

    static class FeatureLimits {
        int databases = 0;
        int allocations = 0;
        int backups = 0;
    }

    static class Allocation {
        @SerializedName("default")
        int defaultAllocation; // Default allocation ID
    }
}
