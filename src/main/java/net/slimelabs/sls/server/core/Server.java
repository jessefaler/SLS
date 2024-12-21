package net.slimelabs.sls.server.core;

import net.slimelabs.sls.api.HttpClient;
import com.google.gson.Gson;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.utils.MinecraftJavaVersionMapper;

public class Server {

    private static final String APPLICATION_API_KEY = "ptla_A0T0M72ZKXYZd73inutGvT0C8s9U1kn6k3dhGAxIOtT"; // Replace with your API key

    private static final String endpoint = "http://panel.slimelabs.net/api/application/servers";
    private static final Gson gson = new Gson();

    public static String createServer(ServerConfiguration serverConfig, String name) throws Exception {
        Payload payload = configurePayload(serverConfig, name);
        String jsonBody = gson.toJson(payload);
        return HttpClient.executePost(endpoint, jsonBody, APPLICATION_API_KEY);
    }

    public static String createServer(Payload payload) throws Exception {
        String jsonBody = gson.toJson(payload);
        return HttpClient.executePost(endpoint, jsonBody, APPLICATION_API_KEY);
    }

    /**
     * Configures the https payload to be sent in the post request.
     * @param serverConfig the server configuration object
     * @return a payload object
     */
    public static Payload configurePayload(ServerConfiguration serverConfig, String name) {
        Payload payload = new Payload();
        payload.user = 1;
        payload.name = name;
        payload.egg = getEggID(serverConfig.software);
        payload.allocation.defaultAllocation = HttpClient.getNextAvailableAllocation();
        payload.docker_image = MinecraftJavaVersionMapper.getRequiredJavaVersion(serverConfig.version);
        payload.startup = "java -Xms128M -Xmx" + serverConfig.ram + " -jar server.jar nogui";
        payload.environment.put("view-distance", String.valueOf(serverConfig.viewDistance));
        payload.limits.io = 100;
        payload.limits.memory = 4096;
        return payload;
    }

    public static int getEggID(String serverSoftware) {
        return switch (serverSoftware) {
            case "sls-paper" -> 15;
            case "sls-spigot" -> 16;
            case "sls-vanilla" -> 17;
            case "mc-paper" -> 2;
            case "mc-vanilla" -> 5;
            default -> throw new InvalidEggException("\"" + serverSoftware + "\" is not a valid server software egg");
        };
    }
}