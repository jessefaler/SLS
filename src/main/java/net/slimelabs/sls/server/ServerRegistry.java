package net.slimelabs.sls.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattmalec.pterodactyl4j.UtilizationState;
import com.velocitypowered.api.command.CommandSource;
import net.slimelabs.sls.server.core.Flags;
import net.slimelabs.sls.server.core.ServerInstance;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerRegistry {

    // ServerName --> ID
    private final HashMap<String, ServerInstance> servers = new HashMap<>();
    private final HashMap<String, Flags> savedFlags = new HashMap<>();

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

    /**
     * Starts a server or creates it if it doesn't exist.
     * @param serverConfiguration the servers configuration data
     * @param name the name of the server
     * @param source the source that called for the server creation i.e., player
     * @return true if no errors occurred
     */
    public boolean startServer(String name, ServerConfiguration serverConfiguration, CommandSource source, Flags flags) {
        flags = configureFlags(name, flags);
        savedFlags.put(name, flags);
        ServerInstance serverInstance = new ServerInstance(name);
        serverInstance.setFlags(flags);
        registerServer(name, serverInstance);
        serverInstance.setSource(source);
        return serverInstance.startServer(serverConfiguration);
    }

    public Flags configureFlags(String name, Flags newFlags) {
        Flags savedFlags = this.savedFlags.get(name);
        if (savedFlags != null) {
            // Override saved flags with new flags
            if (newFlags.SAVE) savedFlags.SAVE = true;
            if (newFlags.VIEW_DISTANCE != 0) savedFlags.VIEW_DISTANCE = newFlags.VIEW_DISTANCE;
            if (newFlags.RAM != 0) savedFlags.RAM = newFlags.RAM;
            if (newFlags.PLAYERS != 0) savedFlags.PLAYERS = newFlags.PLAYERS;
        } else {
            savedFlags = newFlags;
        }
        return newFlags;
    }


    /**
     * Starts a server or creates it if it doesn't exist.
     * @param serverConfiguration the servers configuration data
     * @param name the name of the server
     * @param source the source that called for the server creation i.e., player
     * @return true if no errors occurred
     */
    public boolean startServer(String name, ServerConfiguration serverConfiguration, CommandSource source, ServerInstance serverInstance) {
        registerServer(name, serverInstance);
        serverInstance.setSource(source);
        return serverInstance.startServer(serverConfiguration);
    }

    public void shutdownServer(String name) {
        servers.get(name).shutdown();
    }

    public void killServer(String name) {
        servers.get(name).kill();
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

    public boolean isShutdown(String name) {
        if(!servers.containsKey(name)) {
            return true;
        }
        return servers.get(name).isShutdown();
    }

    // Forcefully deletes the server with the given name
    public void deleteServer(String name) {
        if(servers.containsKey(name)) {
            servers.get(name).deleteServer();
        }
    }

    /**
     * Gets a servers status.
     * online, starting, stopped, offline
     * @return the status of the server
     */
    public UtilizationState getStatus(String name) {
        ServerInstance serverInstance = servers.get(name);
        if(serverInstance == null) {
            return UtilizationState.OFFLINE;
        }
        return serverInstance.state;
    }

    public ServerInstance getServer(String name) {
        return servers.get(name);
    }

    /**
     * Executes a console command on the given server
     * @param command the command to execute
     * @param serverName the name of the server to run the command on
     */
    public void sendCommand(String command, String serverName) {
        servers.get(serverName).sendCommand(command);
    }

    /**
     * Executes a console command on the given server
     * @param command the command to execute
     * @param serverName the name of the server to run the command on
     * @param commandSource the source that is sending the command
     */
    public void sendCommand(String command, String serverName, CommandSource commandSource) {
        servers.get(serverName).sendCommand(command, commandSource);
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
