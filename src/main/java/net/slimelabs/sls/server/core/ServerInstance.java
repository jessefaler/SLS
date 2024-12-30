package net.slimelabs.sls.server.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.api.HttpClient;
import net.slimelabs.sls.api.WebSocketClient;
import net.slimelabs.sls.api.WebSocketMessageListener;
import net.slimelabs.sls.registries.RegistryManager;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessagePreset;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.slimelabs.sls.utils.Color.*;

public class ServerInstance {

    ExecutorService executor = Executors.newSingleThreadExecutor();

    public String id;
    public String name;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    WebSocketClient webSocketClient;
    public String status = "starting";
    WebSocketMessageListener listener;
    public boolean failedToStart;
    boolean outputToProxyConsole;
    CommandSource source;

    public ServerInstance(String name) {
        this.name = name;
    }

    // You can set the source that called the creation of this server (i.e., player) for debugging
    public void setSource(CommandSource source) {
        this.source = source;
    }

    public void sendMessageToSource(Message message) {
        if(source instanceof Player) {
            message.sendMessage(source);
        }
    }

    /**
     * Starts a server or creates it if it doesn't exist.
     * @param serverConfiguration the servers configuration data
     * @return true if no errors occurred
     */
    public boolean startServer(ServerConfiguration serverConfiguration) {
        id = HttpClient.getServerIdByName(name);
        if(id == null) return createServer(serverConfiguration, name); // If the server does not already exist create it.
        try {
            HttpClient.startServer(id);                               // Start the server
            String data = HttpClient.getServerData(id);               // Get the servers data
            String[] allocationData = getAddressFromServerData(data); // Get ip and port from server data
            if(allocationData == null) {
                System.out.println(RED + "Error starting server " + name + " allocation data was null." + RESET);
                HttpClient.killServer(id);
                return false;
            }
            SLS.LOGGER.info("Starting server {}{}{} on port {}{}{} with {}{}{} ram", LIGHT_BLUE, name, RESET, LIGHT_BLUE, allocationData[1], RESET, LIGHT_BLUE, serverConfiguration.ram, RESET);
            establishWebSocketMonitor(id); // Monitor the server
            // Register the server with velocity
            InetSocketAddress address = new InetSocketAddress(allocationData[0], Integer.parseInt(allocationData[1])); // Create socket address
            ServerInfo serverInfo = new ServerInfo(name, address);                                                     // Build server info
            SLS.PROXY.registerServer(serverInfo);
            status = "online";
        } catch (Exception e) {
            System.out.println(RED + "An error occurred while starting the server." + RESET);
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Creates a server using the configuration data and name
     * @param serverConfiguration the servers configuration data
     * @param name the name of the server
     * @return true if no errors occurred
     */
    public boolean createServer(ServerConfiguration serverConfiguration, String name) {
        try {
            String response = Server.createServer(serverConfiguration, name); // Create The Server
            id = getIdentifier(response);                              // Get the servers id
            String data = HttpClient.getServerData(id);                       // Get the servers data
            String[] allocationData = getAddressFromServerData(data);         // Get the servers address from the data
            if(allocationData == null) {
                System.out.println(RED + "Error creating server " + name + " allocation data was null. Shutting down the server." + RESET);
                HttpClient.killServer(id);
                return false;
            }
            SLS.LOGGER.info("Starting server {}{}{} on port {}{}{} with {}{}{} ram", LIGHT_BLUE, name, RESET, LIGHT_BLUE, allocationData[1], RESET, LIGHT_BLUE, serverConfiguration.ram, RESET);
            establishWebSocketMonitor(id); // Monitor the server
            // Register the server with velocity
            InetSocketAddress address = new InetSocketAddress(allocationData[0], Integer.parseInt(allocationData[1])); // Create socket address
            ServerInfo serverInfo = new ServerInfo(name, address);                                                     // Build server info
            SLS.PROXY.registerServer(serverInfo);
            // Register with proxy
        } catch (Exception e) {
            shutdown();
            System.out.println(RED + "An error occurred while creating the server." + RESET);
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Establishes an asynchronous webSocket connection using the WebSocketClient class to monitor the server
     * @param id the id of the server
     */
    public void establishWebSocketMonitor(String id) {
        // The listener is asynchronous and receives WebSocket messages, passing them to the handleWebSocketData method for processing.
        listener = this::handleWebSocketData;
        webSocketClient = new WebSocketClient(id);
        webSocketClient.addMessageListener(listener);
        try {
            webSocketClient.connect();
        } catch (Exception e) {
            System.out.println(RED + "Failed to establish a WebSocket connection to server " + id + ". Shutting down the server." + RESET);
            HttpClient.killServer(id);
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }

    public void handleWebSocketData(String message) {
        try {
            if(message.contains("\"state\\\":\\\"running\\\"")) {
                status = "online";
            } else if (message.contains("\"status\",\"args\":[\"starting\"]")) {
                status = "starting";
            } else if (message.contains("\"status\",\"args\":[\"stopping\"]")) {
                if(status.equals("starting")) { // If the server goes from starting to stopping an error occurred
                    failedToStart = true;
                    failedToStartMessage();
                }
                status = "stopping";
            } else if (message.contains("\"status\",\"args\":[\"offline\"]")) {
                if(status.equals("starting")) { // If the server goes from starting to stopping an error occurred
                    failedToStart = true;
                    failedToStartMessage();
                }
                status = "offline";
                shutdown();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void failedToStartMessage() {
        SLS.LOGGER.error("Error: Failed to start server {}", name.replace("_", " "));
        sendMessageToSource(Message.chat()
                .add(MessagePreset.SLS)
                .add(" Error: Failed to start " + name.replace("_", " "), NamedTextColor.RED));
    }

    // shutdown the server gracefully
    public void shutdown() {
        status = "offline";
        SLS.SERVER_REGISTRY.unRegisterServer(name);
        HttpClient.stopServer(id);
        if(webSocketClient != null) webSocketClient.closeConnection();
        listener = null;
        // Unregister the server in Velocity
        if (SLS.PROXY.getServer(name).isPresent()) {
            SLS.PROXY.unregisterServer(SLS.PROXY.getServer(name).get().getServerInfo());
        }
    }

    /**
     * gets if the server is shutdown
     * @return true if the server is shutdown or stopping
     */
    public boolean isShutdown() {
        return status.equals("stopping") || status.equals("offline");
    }

    // kills the server
    public void kill() {
        if(webSocketClient != null) webSocketClient.closeConnection();
        listener = null;
        HttpClient.killServer(id);
        // Unregister the server in Velocity
        if (SLS.PROXY.getServer(name).isPresent()) {
            SLS.PROXY.unregisterServer(SLS.PROXY.getServer(name).get().getServerInfo());
        }
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
            System.out.println(json);
            throw new IllegalArgumentException("Identifier field not found in JSON.");
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null if an error occurs
        }
    }
}
