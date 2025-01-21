package net.slimelabs.sls.server.core;

import com.google.gson.Gson;
import net.slimelabs.sls.api.Endpoint;
import net.slimelabs.sls.api.HttpClient;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.utils.MinecraftJavaVersionMapper;

import java.nio.file.Paths;
@Deprecated
public class Server {

    private static final String APPLICATION_API_KEY = Endpoint.APPLICATION_API_KEY.getValue(); // Replace with your API key

    private static final String endpoint = "http://panel.slimelabs.net/api/application/servers";
    private static final Gson gson = new Gson();

    public static String createServer(ServerConfiguration serverConfig, String name) {
        Payload payload = configurePayload(serverConfig, name);
        String jsonBody = gson.toJson(payload);
        return HttpClient.executePost(endpoint, jsonBody, APPLICATION_API_KEY);
    }

    public static String createServer(Payload payload) {
        String jsonBody = gson.toJson(payload);
        return HttpClient.executePost(endpoint, jsonBody, APPLICATION_API_KEY);
    }

    /**
     * Configures the https payload to be sent in the post request.
     * @param serverConfig the server configuration object
     * @return a payload object
     */
    public static Payload configurePayload(ServerConfiguration serverConfig, String name) {
        //int allocationid = HttpClient.getNextAvailableAllocation();
        //HttpClient.getAllocationData(allocationid);
        Payload payload = new Payload();
        payload.user = 1;
        payload.name = name;
        payload.egg = getEggID(serverConfig.software);
        payload.allocation.defaultAllocation = HttpClient.getNextAvailableAllocation();
        payload.docker_image = MinecraftJavaVersionMapper.getRequiredJavaVersion(serverConfig.version);
        payload.startup = "java -Xms3072M -XX:MaxRAMPercentage=95.0 -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar";
        payload.environment.put("view-distance", String.valueOf(serverConfig.viewDistance));
        payload.limits.io = 100;
        payload.limits.memory = 4096;
        payload.environment.put("SERVER_PATH", serverConfig.serversFolder + "/" + serverConfig.software + "/" + serverConfig.version);
        String absoluteWorldPath = Paths.get(serverConfig.worldFolder).toAbsolutePath().toString();
        payload.environment.put("WORLD_PATH", absoluteWorldPath);
        return payload;
    }

    public static int getEggID(String serverSoftware) {
        return switch (serverSoftware) {
            case "sls-paper", "sls-fabric" -> 21;
            case "sls-spigot" -> 16;
            case "sls-vanilla" -> 17;
            case "mc-paper" -> 2;
            case "mc-vanilla" -> 5;
            default -> throw new InvalidEggException("\"" + serverSoftware + "\" is not a valid server software egg");
        };
    }
}