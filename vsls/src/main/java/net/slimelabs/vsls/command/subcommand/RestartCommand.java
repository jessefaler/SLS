package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RestartCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("restart")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if(!(source instanceof Player)) {
                        Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                        return 0;
                    }
                    Server server = ServerUtils.getServer((Player) source);
                    if (server != null) {
                        server.restart().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Restarting " + server.getCompositeId(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    RegisteredServer rs = ServerUtils.getRegisteredServer((Player) source);
                                    // Get the players currently connected to the server we are restarting
                                    ArrayList<Player> players = new ArrayList<>(rs.getPlayersConnected());
                                    // Queue them to rejoin when it is ready
                                    queuePlayers(server, players);
                                },
                                failure -> Log.requestError("Failed to restart server " + server.getCompositeId(), failure, source)
                        );
                    } else {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Server " + ServerUtils.getServerName((Player) source) + " is not an SLS server", NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 0;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    Server server = SLS.servers.resolve(id);
                    if (server != null) {
                        server.restart().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Restarting " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);

                                    RegisteredServer rs = ServerUtils.getRegisteredServer((Player) source);
                                    // Get the players currently connected to the server we are restarting
                                    ArrayList<Player> players = new ArrayList<>(rs.getPlayersConnected());
                                    // Queue them to rejoin when it is ready
                                    queuePlayers(server, players);
                                },
                                failure -> Log.requestError("Failed to restart server " + id, failure, source)
                        );
                    } else {
                        // No such server exists
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 0;
                });
    }

    public static void queuePlayers(Server server, List<Player> players) {
        for(Player player : players) {
            server.getEvents().onStatusChange(((status, handle) -> {
                // Wait for the server to change its state to starting
                // before queueing the player
                if(status == ServerStatus.STARTING) {
                    SLS.joinService.joinWhenReady(player, server);
                    handle.remove();
                }
            })).timeout(2, TimeUnit.MINUTES);
            SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
                showRestartTitle(player, server.getName());
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("⚠ ", NamedTextColor.YELLOW)
                        .add(server.getName(), NamedTextColor.GOLD)
                        .add( " is restarting. You will reconnect momentarily.", NamedTextColor.GRAY)
                        .sendMessage(player);
            }).delay(2, TimeUnit.SECONDS).schedule();
        }
    }

    public static void showRestartTitle(Audience target, String serverName) {
        final Component mainTitle = Component.text("Restarting " + serverName, NamedTextColor.YELLOW);
        final Component subtitle = Component.text("You will reconnect momentarily.", NamedTextColor.GREEN);
        final Title title = Title.title(mainTitle, subtitle);
        target.showTitle(title);
    }
}
