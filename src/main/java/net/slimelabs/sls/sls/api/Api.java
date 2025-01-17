package net.slimelabs.sls.api;

import com.mattmalec.pterodactyl4j.PteroAction;
import com.mattmalec.pterodactyl4j.PteroBuilder;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationServer;
import com.mattmalec.pterodactyl4j.application.entities.PteroApplication;
import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.client.entities.PteroClient;
import com.mattmalec.pterodactyl4j.exceptions.LoginException;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessagePreset;
import java.util.List;
import java.util.stream.Collectors;

public class Api {

    public static PteroApplication applicationAPI= PteroBuilder.createApplication("http://panel.slimelabs.net", "SENSITIVE_INFORMATION");
    public static PteroClient clientAPI = PteroBuilder.createClient("http://panel.slimelabs.net", "SENSITIVE_INFORMATION");

    /**
     * Deletes all servers with the given name asynchronously and handles errors.
     * @param name the name of the server
     * @param source the command source to send messages to
     */
    public static void deleteServer(String name, CommandSource source) {
        applicationAPI.retrieveServersByName(name, false).executeAsync(servers -> {
            if (servers.isEmpty()) {
                Message.chat()
                        .add(MessagePreset.SLS)
                        .add("No such server " + name, NamedTextColor.RED)
                        .sendMessage(source);
                return;
            }
            for (ApplicationServer server : servers) {
                server.getController().delete(true).executeAsync(
                        success -> {
                            SLS.SERVER_REGISTRY.unRegisterServer(name);
                            Message.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Deleted " + name.replace("_", " "), NamedTextColor.GRAY)
                                    .sendMessage(source);
                        },
                        throwable -> sendErrorMessage("Failed to delete server: " + server.getName(), source)
                );
            }
        }, throwable -> {
            if (throwable instanceof LoginException) {
                sendErrorMessage("Failed to retrieve servers: Invalid API key or insufficient permissions", source);
            } else {
                sendErrorMessage("Failed to retrieve servers: " + throwable.getMessage(), source);
            }
        });
    }

    /**
     * Deletes all servers with the given name asynchronously and handles errors.
     * @param name the name of the server
     */
    public static void deleteServer(String name) {
        applicationAPI.retrieveServersByName(name, false).executeAsync(servers -> {
            if (servers.isEmpty()) {
                return;
            }
            for (ApplicationServer server : servers) {
                server.getController().delete(true).executeAsync(
                        success -> {
                            SLS.SERVER_REGISTRY.unRegisterServer(name);
                        }
                );
            }
        });
    }

    /**
     * Retrieves all server names using the clientAPI and returns a PteroAction of a list of server names.
     * @return PteroAction of a list of server names
     */
    public static PteroAction<List<String>> getAllServerNames() {
        return clientAPI.retrieveServers().map(clientServers ->
                clientServers.stream()
                        .map(ClientServer::getName)
                        .collect(Collectors.toList())
        );
    }

    public static PteroAction<List<ClientServer>> getClientServer(String name) {
        return clientAPI.retrieveServersByName(name, false);
    }

    /**
     * Sends a message to the source and console
     */
    public static void sendMessage(String message, CommandSource source) {
        if (source instanceof Player) {
            Message.chat().add(MessagePreset.SLS).add("API Message: ", NamedTextColor.GRAY).add(message, NamedTextColor.DARK_AQUA);
        }
        SLS.LOGGER.info("API Message: {}", message);
    }

    /**
     * Sends an error message to the source and console
     * [SLS] and
     */
    public static void sendErrorMessage(String message, CommandSource source) {
        if (source instanceof Player) {
            Message.chat().add(MessagePreset.SLS).add("Error: ", NamedTextColor.RED).add(message, TextColor.color(224, 27, 36));
        }
        SLS.LOGGER.error(message);
    }
}
