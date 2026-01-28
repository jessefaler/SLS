package net.slimelabs.vsls.command.subcommand;

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
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Handles the execution of the reset command (/sls reset).
 * <p>
 * The reset command deletes all saved data related to the server and world for a specified server.
 * It performs the following actions:
 * <ul>
 *   <li>Stops the server.</li>
 *   <li>Deletes the contents of the upper and work directories in the server and world folder.</li>
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
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Optional<ServerConnection> server;
                    if(source instanceof Player player) {
                        server = player.getCurrentServer();
                        if(server.isEmpty()) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("An error occurred while resetting. The server was not found", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 1;
                        }
                    } else {
                        SLS.logger.warn("Invalid command usage! You must specify a server id when running this command from console.");
                        return 0;
                    }

                    // Get the players currently connected to the server we are resetting
                    ArrayList<Player> players = new ArrayList<>(server.get().getServer().getPlayersConnected());
                    // Get the name of the server
                    String serverName = server.get().getServerInfo().getName();

                    try {
                        reset(serverName, source, players);
                    } catch (ServerNotFoundException e) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add(serverName + " cannot be reset.", NamedTextColor.RED)
                                .sendMessage(source);
                    }

                    return 1;
                })
                .then(server());
    }

    // /sls reset
    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.servers.getIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    Optional<RegisteredServer> server = SLS.proxy.getServer(id);
                    if(server.isEmpty()) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }

                    // Get the players currently connected to the server we are resetting
                    ArrayList<Player> players = new ArrayList<>(server.get().getPlayersConnected());

                    try {
                        reset(id, source, players);
                    } catch (ServerNotFoundException e) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add(e.getMessage(), NamedTextColor.RED)
                                .sendMessage(source);
                    }

                    return 1;
                });
    }

    public static class ServerNotFoundException extends RuntimeException {
        public ServerNotFoundException(String serverId) {
            super("Server '" + serverId + "' does not exist in sls");
        }
    }

    /**
     * Resets the server instance and if it's running it will reconnect the provided list of players
     * @param serverId the servers id
     * @param source the command source who called the reset
     * @param players the players to reconnect once it is reset
     * @throws ServerNotFoundException if the server doesn't exist in sls
     */
    public static void reset(String serverId, CommandSource source, ArrayList<Player> players) throws ServerNotFoundException {
        Server server = SLS.servers.getServer(serverId);
        if(server == null) {
            throw new ServerNotFoundException(serverId);
        }

        server.reset().executeAsync(success -> {
            for(Player player : players) {
                SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
                    // Queue the player to reconnect to the server
                    SLS.queue.enqueue(player, server);
                    showResetTitle(player, server.name);
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("⚠ ", NamedTextColor.YELLOW)
                            .add(server.name, NamedTextColor.GOLD)
                            .add( " is resetting. You will be automatically reconnected.", NamedTextColor.GRAY)
                            .sendMessage(player);
                }).delay(2, TimeUnit.SECONDS).schedule();
            }
        }, failure -> {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .add("Failed to reset " + serverId + " Reason: " + failure.getMessage(), NamedTextColor.RED)
                    .sendMessage(source);
        });
    }

    public static void showResetTitle(Audience target, String serverName) {
        final Component mainTitle = Component.text("Resetting " + serverName, NamedTextColor.YELLOW);
        final Component subtitle = Component.text("You’ll be reconnected automatically.", NamedTextColor.GREEN);
        final Title title = Title.title(mainTitle, subtitle);
        target.showTitle(title);
    }
}
