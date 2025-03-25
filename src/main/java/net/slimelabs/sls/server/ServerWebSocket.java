package net.slimelabs.sls.server;

import com.mattmalec.pterodactyl4j.PowerAction;
import com.mattmalec.pterodactyl4j.PteroBuilder;
import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.client.entities.PteroClient;
import com.mattmalec.pterodactyl4j.client.managers.WebSocketBuilder;
import com.mattmalec.pterodactyl4j.client.managers.WebSocketManager;
import com.mattmalec.pterodactyl4j.client.ws.events.AuthSuccessEvent;
import com.mattmalec.pterodactyl4j.client.ws.events.StatsUpdateEvent;
import com.mattmalec.pterodactyl4j.client.ws.events.connection.DisconnectingEvent;
import com.mattmalec.pterodactyl4j.client.ws.events.output.ConsoleOutputEvent;
import com.mattmalec.pterodactyl4j.client.ws.events.output.InstallOutputEvent;
import com.mattmalec.pterodactyl4j.client.ws.events.output.OutputEvent;
import com.mattmalec.pterodactyl4j.client.ws.hooks.ClientSocketListenerAdapter;
import com.velocitypowered.api.command.CommandSource;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.api.Endpoint;
import net.slimelabs.sls.server.core.ServerInstance;
import net.slimelabs.sls.utils.Message.ProtoMessage;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static net.slimelabs.sls.api.Endpoint.CLIENT_API_KEY;
import static net.slimelabs.sls.api.Endpoint.CLIENT_API_URL;

/**
 * Manages a WebSocket connection to a server instance.
 * <p>
 * This class is responsible for monitoring the server's status, sending commands,
 * and handling unexpected crashes or failures during server startup.
 * </p>
 * <p>
 * It extends {@link ClientSocketListenerAdapter} to provide WebSocket event-handling
 * capabilities tailored for server management tasks.
 * </p>
 */
public class ServerWebSocket extends ClientSocketListenerAdapter {

    CommandSource commandSource; // Used for the send command method
    boolean output;

    public Watcher watcher;

    private WebSocketManager webSocketManager;
    private final ServerInstance serverInstance; // The ServerInstance that this websocket is connected to

    public ServerWebSocket(String id, ServerInstance serverInstance) {
        this.serverInstance = serverInstance;
        PteroClient api = PteroBuilder.createClient(CLIENT_API_URL.getValue(), CLIENT_API_KEY.getValue());
        api.retrieveServerByIdentifier(id).map(ClientServer::getWebSocketBuilder)
                .map(builder -> builder.addEventListeners(this))
                .executeAsync(WebSocketBuilder::build, throwable -> {
                    cleanup();
                    System.err.println("Failed to establish a websocket connection to server " + serverInstance.name + ". Aborting startup.");
                    System.err.println(throwable.getMessage());
                });
    }

    @Override
    public void onAuthSuccess(AuthSuccessEvent event) {
        this.webSocketManager = event.getWebSocketManager();
        webSocketManager.request(WebSocketManager.RequestAction.LOGS);
        if(serverInstance.state == UtilizationState.OFFLINE) restartTask();
    }

    /**
     * Schedules a task to start the server every 4 seconds if no error ever occurred and the server is still showing offline.
     */
    private void restartTask() {
        Executors.newScheduledThreadPool(1).schedule(() -> {
            if(serverInstance.state == UtilizationState.OFFLINE) {
                this.setState(PowerAction.START);
                restartTask();
            }
        }, 4, TimeUnit.SECONDS);
    }

    @Override
    public void onDisconnecting(DisconnectingEvent event) {
        cleanup();
    }

    @Override
    public void onOutput(OutputEvent event) {
    }

    @Override
    public void onConsoleOutput(ConsoleOutputEvent event) {
        handleConsoleErrors(event.getLine());
        // Used for sending the next line of console output to a player who ran a
        // console command using /sls console
        handleConsoleMessage(commandSource, event.getLine());
        if (watcher != null) watcher.forwardConsoleOutput(event.getLine());
    }

    @Override
    public void onInstallOutput(InstallOutputEvent event) {
    }

    @Override
    public void onStatsUpdate(StatsUpdateEvent event) {
        serverInstance.metrics = event;
        handleStateInfo(event.getState());
    }

    public void requestStats() {
        webSocketManager.request(WebSocketManager.RequestAction.STATS);
    }

    public void requestLogs() {
        webSocketManager.request(WebSocketManager.RequestAction.LOGS);
    }

    public void setState(PowerAction powerAction) {
        webSocketManager.setPower(powerAction);
    }

    public void sendCommand(String command) {
        webSocketManager.sendCommand(command);
    }

    public void sendCommand(String command, CommandSource commandSource) {
        webSocketManager.sendCommand(command);
        this.commandSource = commandSource;
    }

    public void closeConnection() {
        if (webSocketManager != null) {
            try {
                webSocketManager.shutdown();
            } catch (IllegalStateException ignored) {}
        }
    }

    private void handleConsoleErrors(String message) {
        if(message.equals("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Exit code: 1\u001B[0m")) { // Exit code 1 indicates server terminated with an error
            SLS.LOGGER.error("Failed to start {} Exit code: 1", serverInstance.name);
            cleanup();
        } else if (message.contains("Out of memory: true")) {
            SLS.LOGGER.error("Failed to start {} Out of memory", serverInstance.name);
            cleanup();
        } else if (message.equals("\u001B[33m\u001B[1m[Pterodactyl Daemon]:\u001B[39m Exit code: 128\u001B[0m")) {
            SLS.LOGGER.error("Failed to start {}, Exit code: 128. This is likely ip Address/Port allocation issue. Ensure the allocations in the target node match the systems ip.", serverInstance.name);
            cleanup();
        } else if (message.contains(" INFO]: Closing Server") || message.contains("Server marked as offline...")) {
            SLS.LOGGER.error("Failed to start {} Server closed", serverInstance.name);
            cleanup();
        }
    }

    private void handleStateInfo(UtilizationState state) {
        serverInstance.state = state; // Set the state variable in the serverInstance
    }

    private void cleanup() {
        closeConnection(); // Close the websocket connection
        serverInstance.shutdown();
    }


    /**
     * Handles sending a response when a player uses the /sls console command
     * @param source the source that sends the console command
     * @param message the response message
     */
    public void handleConsoleMessage(CommandSource source, String message) {
        if(commandSource != null) {
            if(output) {
                message = message.replaceAll("\u001b\\[[;\\d]*m", ""); // Remove color codes
                message = message.replaceAll(">\u001B\\[2K", ""); // Remove control characters
                message = message.replace("\r", ""); // Remove CR control code
                if(message.contains("Unknown or incomplete command")) {
                    ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                            + serverInstance.name.replace("_", " ")
                            + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                            + message + "</red>").sendMessage(source);
                    return;
                }
                if(message.contains("<--[HERE]")) {
                    ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                            + serverInstance.name.replace("_", " ")
                            + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                            + message + "</red>").sendMessage(source);
                } else {
                    ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                            + serverInstance.name.replace("_", " ")
                            + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>"
                            + message + "</gray>").sendMessage(source);
                }
                output = false;
                commandSource = null;
                return;
            }
            output = true;
        }
    }
}