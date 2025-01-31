package net.slimelabs.sls.command.subcommand;

import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.client.entities.Directory;
import com.mattmalec.pterodactyl4j.client.entities.GenericFile;
import com.mattmalec.pterodactyl4j.client.managers.DeleteAction;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.server.Flags;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.server.core.ServerInstance;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.StringUtils;

import java.util.ArrayList;
import java.util.Optional;

import static net.slimelabs.sls.api.API.*;

/**
 * Handles the execution of the reset command (/sls reset).
 * <p>
 * The reset command deletes all saved data related to the server and world for a specified server.
 * It performs the following actions:
 * <ul>
 *   <li>Kills the server.</li>
 *   <li>Deletes the contents of the upperdir directories in the server and world folder.</li>
 *   <li>Sends all players connected to the server being reset to a fallback server.</li>
 *   <li>Starts the server.</li>
 *   <li>Queues the players to join the server once it's reset.</li>
 * </ul>
 * <p>
 * When executed without arguments, the command will reset the server the executor is currently connected to,
 * provided the server can be reset. If a server argument is supplied, the specified server will be reset instead.
 */
public class ResetCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("reset")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Player player = (Player) source;
                    String serverName;
                    Optional<ServerConnection> server = player.getCurrentServer();
                    if(server.isEmpty()) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("An error occurred while resetting. The server was not found", NamedTextColor.RED)
                                .sendMessage(source);
                        return 1;
                    }
                    serverName = server.get().getServerInfo().getName();
                    if(!SLS.REGISTRY_MANAGER.doseWorldExist(StringUtils.truncateAtPeriod(serverName))) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add(serverName.replace("_", " ") + " cannot be reset.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 1;
                    }
                    ArrayList<Player> players = new ArrayList<>(server.get().getServer().getPlayersConnected());

                    Thread thread = new Thread(() -> {
                        for(Player connectPlayer : players) {
                            getFallbackServer(connectPlayer).ifPresentOrElse(
                                    targetServer -> connectPlayer.createConnectionRequest(targetServer).connectWithIndication().thenAccept(connection -> {
                                        reset(serverName, source, players);
                                    }).exceptionally(throwable -> {
                                        reset(serverName, source, players);
                                        // Handle connection failure
                                        ProtoMessage.chat()
                                                .add(MessagePreset.SLS)
                                                .add("Error: Could not connect to " + serverName, NamedTextColor.RED)
                                                .sendMessage(connectPlayer);
                                        SLS.LOGGER.error("There was an error while connecting {} to {} ensure the ip and port are configured correctly and the server is accessible through velocity", connectPlayer.getUsername(), serverName);
                                        System.err.println(throwable.getMessage());
                                        return null;
                                    }),
                                    () -> {
                                        reset(serverName, source, players);
                                        ProtoMessage.chat()
                                                .add(MessagePreset.SLS)
                                                .add("Error: Server not found", NamedTextColor.RED)
                                                .sendMessage(player);
                                    }
                            );
                        }});
                    thread.start();
                    return 1;
                })
                .then(server());
    }

    // /sls reset
    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.SERVER_REGISTRY.getServerNames().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String serverName = StringArgumentType.getString(context, "server");
                    if(!SLS.REGISTRY_MANAGER.doseWorldExist(StringUtils.truncateAtPeriod(serverName))) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add(serverName.replace("_", " ") + " cannot be reset.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 1;
                    }
                    if(!SLS.SERVER_REGISTRY.containsServer(serverName)) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Server " + serverName + " was not found.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    ArrayList<Player> players = new ArrayList<>(SLS.PROXY.getServer(serverName).get().getPlayersConnected());

                    Thread thread = new Thread(() -> {
                        for(Player connectPlayer : players) {
                            getFallbackServer(connectPlayer).ifPresentOrElse(
                                    targetServer -> connectPlayer.createConnectionRequest(targetServer).connectWithIndication().thenAccept(connection -> {
                                        reset(serverName, source, players);
                                    }).exceptionally(throwable -> {
                                        reset(serverName, source, players);
                                        // Handle connection failure
                                        ProtoMessage.chat()
                                                .add(MessagePreset.SLS)
                                                .add("Error: Could not connect to " + serverName, NamedTextColor.RED)
                                                .sendMessage(connectPlayer);
                                        SLS.LOGGER.error("There was an error while connecting {} to {} ensure the ip and port are configured correctly and the server is accessible through velocity", connectPlayer.getUsername(), serverName);
                                        System.err.println(throwable.getMessage());
                                        return null;
                                    }),
                                    () -> {
                                        reset(serverName, source, players);
                                        ProtoMessage.chat()
                                                .add(MessagePreset.SLS)
                                                .add("Error: Server not found", NamedTextColor.RED)
                                                .sendMessage(source);
                                    }
                            );
                        }});
                    thread.start();
                    return 1;
                });
    }

    /**
     * Sends a connection request to join the given player to the specified server
     * @param player the player instance to connect
     * @param serverName the name of the server
     */
    public static void sendConnectionRequest(Player player, String serverName) {
        SLS.PROXY.getServer(serverName).ifPresentOrElse(
                targetServer -> player.createConnectionRequest(targetServer).connectWithIndication().thenAccept(connection -> {
                }).exceptionally(throwable -> {
                    // Handle connection failure
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Error: Could not connect to " + serverName, NamedTextColor.RED)
                            .sendMessage(player);
                    SLS.LOGGER.error("There was an error while connecting {} to {} ensure the ip and port are configured correctly and the server is accessible through velocity", player.getUsername(), serverName);
                    System.err.println(throwable.getMessage());
                    return null;
                }),
                () -> ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("Error: Server not found", NamedTextColor.RED)
                        .sendMessage(player)
        );
    }

    public static void reset(String serverName, CommandSource source, ArrayList<Player> players) {
        if(SLS.PROXY.getServer(serverName).get().getPlayersConnected().isEmpty()) {
            ServerInstance serverInstance = SLS.SERVER_REGISTRY.getServer(serverName);
            Flags flags = serverInstance.flags;
            ServerConfiguration serverConfiguration = serverInstance.serverConfiguration;

            clientAPI.retrieveServersByName(serverName, false).executeAsync(servers -> {
                if (servers.isEmpty()) {
                    return;
                }
                // Make a call to the api to delete the server and then on success recreate the server and send the previously connected players to it
                for (ClientServer clientServer : servers) {
                    clientServer.kill().executeAsync(
                            success -> {
                                // Delete all files in the /world/upperdir
                                Directory directory = clientServer.retrieveDirectory("/world/upperdir").execute();
                                DeleteAction delete = directory.deleteFiles();
                                for(GenericFile file : directory.getFiles()) {
                                    delete.addFile(file);
                                }
                                delete.execute();

                                // Delete all files in the /server/upperdir
                                directory = clientServer.retrieveDirectory("/server/upperdir").execute();
                                delete = directory.deleteFiles();
                                for(GenericFile file : directory.getFiles()) {
                                    delete.addFile(file);
                                }
                                delete.execute();

                                SLS.SERVER_REGISTRY.startServer(serverName, serverConfiguration, source, flags);
                                ProtoMessage.chat()
                                        .add(MessagePreset.SLS)
                                        .add("Resetting " + serverName.replace("_", " "), NamedTextColor.GRAY)
                                        .sendMessage(source);
                                for(Player connectPlayer1 : players) {
                                    showResetTitle(connectPlayer1, serverName);
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("⚠ ", NamedTextColor.YELLOW)
                                            .add(serverName.replace("_", " "), NamedTextColor.GOLD)
                                            .add( " is resetting. You will be automatically reconnected.", NamedTextColor.GRAY)
                                            .sendMessage(connectPlayer1);
                                    SLS.PLAYER_CONNECTOR.joinServer(serverName, serverConfiguration.registry, connectPlayer1);
                                }
                            },
                            throwable -> {}
                    );
                }
            });
        }
    }

    public static void showResetTitle(Audience target, String serverName) {
        final Component mainTitle = Component.text("Resetting " + StringUtils.truncateAtPeriod(serverName).replace("_", " "), NamedTextColor.YELLOW);
        final Component subtitle = Component.text("You’ll be reconnected automatically.", NamedTextColor.GREEN);

        final Title title = Title.title(mainTitle, subtitle);
        target.showTitle(title);
    }

    public static Optional<RegisteredServer> getFallbackServer(Player player) {
        // Get all available servers
        return SLS.PROXY.getAllServers()
                .stream()
                .filter(server -> serverCanBeUsedAsFallback(player, server))
                .findFirst(); // Return the first available fallback server
    }

    private static boolean serverCanBeUsedAsFallback(Player player, RegisteredServer server) {
        // Implement your logic here, e.g., check if the server is not full, is available, etc.
        return true; // Placeholder for demo purposes
    }
}
