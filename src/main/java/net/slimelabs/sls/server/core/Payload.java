package net.slimelabs.sls.server.core;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the payload data used in a HTTP POST request to configure a server.
 * This class includes properties such as server configuration, limits, feature limits,
 * environment variables, and Docker image setup for server deployment.
 */
@Deprecated
public class Payload {
    String name = "";
    int user = 1;  // User ID
    int egg = 2;   // Egg ID
    String docker_image = "sls:latest";
    String startup = "java -Xms3072M -XX:MaxRAMPercentage=95.0 -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar";
    Limits limits = new Limits();
    FeatureLimits feature_limits = new FeatureLimits();
    Allocation allocation = new Allocation();
    Map<String, String> environment = new HashMap<>();

    public Payload() {
        environment.put("MINECRAFT_VERSION", "1.20.6");
        environment.put("SERVER_SOFTWARE", "sls-paper");
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
