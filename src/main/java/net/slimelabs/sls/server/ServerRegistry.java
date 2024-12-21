package net.slimelabs.sls.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocitypowered.api.command.CommandSource;
import net.slimelabs.sls.server.core.ServerInstance;

import java.util.HashMap;
import java.util.Set;

public class ServerRegistry {

    // ServerName --> ID
    private final HashMap<String, ServerInstance> servers = new HashMap<>();

    /**
     * Starts a server or creates it if it doesn't exist.
     * @param serverConfiguration the servers configuration data
     * @param name the name of the server
     * @return true if no errors occurred
     */
    public boolean startServer(String name, ServerConfiguration serverConfiguration) {
        ServerInstance serverInstance = new ServerInstance(name);
        registerServer(name, serverInstance);
        return serverInstance.startServer(serverConfiguration);
    }

    /**
     * Starts a server or creates it if it doesn't exist.
     * @param serverConfiguration the servers configuration data
     * @param name the name of the server
     * @param source the source that called for the server creation i.e., player
     * @return true if no errors occurred
     */
    public boolean startServer(String name, ServerConfiguration serverConfiguration, CommandSource source) {
        ServerInstance serverInstance = new ServerInstance(name);
        registerServer(name, serverInstance);
        serverInstance.setSource(source);
        return serverInstance.startServer(serverConfiguration);
    }

    public void shutdownServer(String name) {
        servers.get(name).shutdown();
    }

    public void registerServer(String name, ServerInstance serverInstance) {
        servers.put(name, serverInstance);
    }

    public void unRegisterServer(String name) {
        servers.remove(name);
    }

    public boolean containsServer(String name) {
        return servers.containsKey(name);
    }

    public Set<String> getServerNames() {
        return servers.keySet();
    }

    /**
     * Gets a servers status.
     * online, starting, stopped, offline
     * @return the status of the server
     */
    public String getStatus(String name) {
        return servers.get(name).status;
    }

    /**
     * Checks if the server is online, indicating it is ready to accept players.
     *
     * @param name the name of the server to check
     * @return {@code true} if the server is online, {@code false} otherwise
     */
    public boolean isOnline(String name) {
        return getStatus(name).equals("online");
    }

    public boolean isStopping(String name) {
        return getStatus(name).equals("stopping");
    }

    // -------------------- Utility and Helper Methods -----------------------------

    // Method to extract IP and port from the JSON string
    public static String[] getAddressFromServerData(String jsonString) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // Parse the JSON string into a JsonNode
            JsonNode rootNode = objectMapper.readTree(jsonString);

            // Access the 'relationships' -> 'allocations' -> 'data' -> first element
            JsonNode allocation = rootNode.path("attributes").path("relationships")
                    .path("allocations").path("data").get(0);

            // Extract IP and port from the first allocation
            String ip = allocation.path("attributes").path("ip").asText();
            String port = allocation.path("attributes").path("port").asText();
            return new String[]{ip, port};
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getIdentifier(String json) {
        try {
            // Create an ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();

            // Parse the JSON string into a JsonNode
            JsonNode rootNode = objectMapper.readTree(json);

            // Navigate to the "attributes" object and extract the "identifier"
            JsonNode attributes = rootNode.get("attributes");
            if (attributes != null) {
                JsonNode identifierNode = attributes.get("identifier");
                if (identifierNode != null) {
                    return identifierNode.asText(); // Return the identifier value
                }
            }
            throw new IllegalArgumentException("Identifier field not found in JSON.");
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null if an error occurs
        }
    }


}
