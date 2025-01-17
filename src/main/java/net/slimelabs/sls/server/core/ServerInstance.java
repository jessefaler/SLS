package net.slimelabs.sls.server.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattmalec.pterodactyl4j.DataType;
import com.mattmalec.pterodactyl4j.EnvironmentValue;
import com.mattmalec.pterodactyl4j.PteroAction;
import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationAllocation;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationEgg;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationServer;
import com.mattmalec.pterodactyl4j.application.entities.Node;
import com.mattmalec.pterodactyl4j.client.entities.ClientAllocation;
import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.entities.Allocation;
import com.mattmalec.pterodactyl4j.exceptions.LoginException;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.api.Api;
import net.slimelabs.sls.api.HttpClient;
import net.slimelabs.sls.api.NoAvailableAllocationsException;
import net.slimelabs.sls.server.ServerWebSocket;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.MinecraftJavaVersionMapper;

import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.slimelabs.sls.api.Api.*;
import static net.slimelabs.sls.utils.Color.*;

public class ServerInstance {

    public String name;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    ServerWebSocket serverWebSocket;
    public UtilizationState state = UtilizationState.OFFLINE;
    public boolean shutdown;
    boolean outputToProxyConsole;
    CommandSource source;
    ClientServer clientServer;
    String identifier;
    Flags FLAGS;

    public ServerInstance(String name) {
        this.name = name;
        this.FLAGS = new Flags();
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
        clientAPI.retrieveServersByName(name, false).executeAsync(servers -> {
            if (servers.isEmpty()) { // No server already exists so create it
                createServer(serverConfiguration, name);
                return;
            }
            if(servers.size() > 1) {
                System.err.println("Found multiple servers with the name '\" + name + \"' while starting. Ignoring all but the first one.");
            }
            ClientServer clientServer = servers.get(0);
            clientServer.start().executeAsync(
                        success -> {
                            SLS.LOGGER.info("Starting server {}{}{} with address {}{}{} with {}{}{} ram", LIGHT_BLUE, name, RESET, LIGHT_BLUE, clientServer.getPrimaryAllocation().getFullAddress(), RESET, LIGHT_BLUE, serverConfiguration.ram, RESET);
                            registerServer(clientServer); // Register the server
                        },
                        throwable -> sendErrorMessage("Failed to start server: " + clientServer.getName(), source)
                );
        }, throwable -> {
            if (throwable instanceof LoginException) {
                sendErrorMessage("Failed to retrieve servers: Invalid API key or insufficient permissions", source);
            } else {
                sendErrorMessage("Failed to retrieve servers: " + throwable.getMessage(), source);
            }
        });
        return true;
    }

    public void registerServer(ClientServer clientServer) {
        identifier = clientServer.getIdentifier();
        this.clientServer = clientServer; // Set the clientServer object
        establishWebSocketMonitor(clientServer.getIdentifier()); // Monitor the server
        // Register the server with velocity
        ClientAllocation allocation = clientServer.getPrimaryAllocation();
        InetSocketAddress address = new InetSocketAddress(allocation.getIP(), allocation.getPortInt()); // Create socket address
        ServerInfo serverInfo = new ServerInfo(name, address);                                          // Build server info
        SLS.PROXY.registerServer(serverInfo); // Register the server
    }

    /**
     * Creates a server using the configuration data and name
     * @param serverConfiguration the servers configuration data
     * @param name the name of the server
     * @return true if no errors occurred
     */
    public boolean createServer(ServerConfiguration serverConfiguration, String name) {
        // Configure Environment Variables
        String serverPath = serverConfiguration.serversFolder + "/" + serverConfiguration.software + "/" + serverConfiguration.version;
        String absoluteWorldPath = Paths.get(serverConfiguration.worldFolder).toAbsolutePath().toString();
        Map<String, EnvironmentValue<?>> environmentVariables = new HashMap<>();
        environmentVariables.put("SERVER_PATH", EnvironmentValue.of(serverPath));
        environmentVariables.put("WORLD_PATH", EnvironmentValue.of(absoluteWorldPath));

        // Configure the server
        PteroAction<ApplicationServer> action = applicationAPI.createServer()
                .setName(name)
                .setOwner(applicationAPI.retrieveUserById(1).execute())
                .setDescription("SLS " + serverConfiguration.registry + " server.")
                .setMemory(4, DataType.GB)
                .skipScripts(true)
                .startOnCompletion(true)
                .setAllocation(getNextAvailableAllocation())
                .setEgg(applicationAPI.retrieveEggById(applicationAPI.retrieveNestById(9).execute(), getEggID(serverConfiguration.software)).execute())
                .setDockerImage(MinecraftJavaVersionMapper.getRequiredJavaVersion(serverConfiguration.version))
                .setEnvironment(environmentVariables)
                .setStartupCommand(getStartCommand("3072"));

        // Create the server
        action.executeAsync(
                ApplicationServer -> {
                    identifier = ApplicationServer.getIdentifier();
                    // Get the ClientServer
                    clientAPI.retrieveServerByIdentifier(ApplicationServer.getIdentifier()).executeAsync(
                            clientServer -> {
                                SLS.LOGGER.info("Starting server {}{}{} with address {}{}{} with {}{}{} ram", LIGHT_BLUE, name, RESET, LIGHT_BLUE, clientServer.getPrimaryAllocation().getFullAddress(), RESET, LIGHT_BLUE, serverConfiguration.ram, RESET);
                                registerServer(clientServer); // Register the server
                                },
                            throwable -> {
                                shutdown();
                                SLS.LOGGER.error("Failed to retrieve client server: {}", throwable.getMessage());
                            });
                    },
                failure -> sendErrorMessage("Failed to create server: " + name, source));
        return true;
    }

    private String getStartCommand(String ram) {
        // Optimised start flags by Aikar, see: https://docs.papermc.io/misc/tools/start-script-gen
        return "java -Xms" + 500 + "M " +
                "-XX:MaxRAMPercentage=95.0 " +
                "-Dterminal.jline=false " +
                "-Dterminal.ansi=true " +
                "-jar server.jar nogui";
    }



    public ApplicationAllocation getNextAvailableAllocation() {
        Node node = applicationAPI.retrieveNodeById(1).execute();
        for (ApplicationAllocation allocation : node.retrieveAllocations()) {
            if (!allocation.isAssigned()) {
                return allocation;
            }
        }
        throw new NoAvailableAllocationsException();
    }

    public static long getEggID(String serverSoftware) {
        return switch (serverSoftware) {
            case "sls-paper", "sls-fabric" -> 21;
            case "sls-spigot" -> 16;
            case "sls-vanilla" -> 17;
            case "mc-paper" -> 2;
            case "mc-vanilla" -> 5;
            default -> throw new InvalidEggException("\"" + serverSoftware + "\" is not a valid server software egg");
        };
    }

    /**
     * Establishes an asynchronous webSocket connection using the WebSocketClient class to monitor the server
     * @param id the id of the server
     */
    public void establishWebSocketMonitor(String id) {
        serverWebSocket = new ServerWebSocket(id, this);
    }

    public void failedToStartMessage() {
        SLS.LOGGER.error("Error: Failed to start server {}", name.replace("_", " "));
        sendMessageToSource(Message.chat()
                .add(MessagePreset.SLS)
                .add(" Error: Failed to start " + name.replace("_", " "), NamedTextColor.RED));
    }

    // shutdown the server gracefully
    public void shutdown() {
        if(!FLAGS.SAVE) {
            deleteServerSilent(); // Delete the server if saving is not enabled
        } else {
            HttpClient.stopServer(identifier);
        }
        shutdown = true;
        if(serverWebSocket != null) serverWebSocket.closeConnection();
        // Unregister the server in Velocity
        if (SLS.PROXY.getServer(name).isPresent()) {
            SLS.PROXY.unregisterServer(SLS.PROXY.getServer(name).get().getServerInfo());
        }
        SLS.SERVER_REGISTRY.unRegisterServer(name);
    }

    // kills the server
    public void kill() {
        if(!FLAGS.SAVE) {
            deleteServerSilent(); // Delete the server if saving is not enabled
        } else {
            HttpClient.killServer(identifier);
        }
        if(serverWebSocket != null) serverWebSocket.closeConnection();
        // Unregister the server in Velocity
        if (SLS.PROXY.getServer(name).isPresent()) {
            SLS.PROXY.unregisterServer(SLS.PROXY.getServer(name).get().getServerInfo());
        }
    }

    public void sendCommand(String command) {
        serverWebSocket.sendCommand(command);
    }

    public void sendCommand(String command, CommandSource commandSource) {
        serverWebSocket.sendCommand(command, commandSource);
    }

    /**
     * gets if the server is shutdown
     * @return true if the server is shutdown or stopping
     */
    public boolean isShutdown() {
        return state.equals(UtilizationState.STOPPING) || state.equals(UtilizationState.OFFLINE);
    }

    public void setFlags(Flags flags) {
        this.FLAGS = flags;
    }

    // Forcefully deletes the server
    public void deleteServer() {
        Api.deleteServer(name, source);
    }

    // Forcefully deletes the server
    public void deleteServerSilent() {
        Api.deleteServer(name);
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
}
