package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Objects;
import java.util.Optional;

public class KillCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("kill")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if(!(source instanceof Player)) {
                        Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                        return 0;
                    }
                    Server server = ServerUtils.getServer((Player) source);
                    if (server != null) {
                        server.kill().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Killed " + server.getShortId(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to kill server " + server.getShortId(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    Log.warn("Failed to kill server " + server.getShortId() + " reason: " + failure.getMessage());
                                }
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
                    builder.suggest("all");
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    if(id.equals("all")) {
                        if(SLS.servers.getAll().isEmpty()) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("No servers are running.", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 1;
                        }
                        SLS.servers.getAll().forEach(server ->
                                server.kill().executeAsync(
                                        success -> {},
                                        failure -> Log.warn("Failed to kill server " + server.getId())
                                )
                        );
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Killing all servers.", NamedTextColor.GRAY)
                                .sendMessage(source);
                        return 1;
                    }
                    // Shutdown server
                    Server server = SLS.servers.resolve(id);
                    if (server != null) {
                        server.kill().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Killed " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to kill server " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    Log.warn("Failed to kill server " + server.getId() + " reason: " + failure.getMessage());
                                }
                        );
                    } else {
                        // No such server exists
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 0;
                }).then(force());
    }

    /**
     * Unregisters the server regardless of whether it was killed successfully.
     */
    private static RequiredArgumentBuilder<CommandSource, String> force() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("force", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("force");
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    String force = StringArgumentType.getString(context, "force");
                    if(!Objects.equals(force, "force")) {
                        ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                        ProtoMessage.chat()
                                .add(MessageFormatter.commandUsage("/sls kill " + id, "force"))
                                .sendMessage(source);
                        return 0;
                    }

                    if(id.equals("all")) {
                        if(SLS.servers.getAll().isEmpty()) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("No servers are running.", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 1;
                        }
                        SLS.servers.getAll().forEach(server ->
                                server.kill().executeAsync(
                                        success -> {},
                                        failure -> {
                                            Log.warn("Failed to kill server " + server.getId() + " reason: " + failure.getMessage());
                                            SLS.servers.unRegister(server.getId());
                                        }
                                )
                        );
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Killing all servers.", NamedTextColor.GRAY)
                                .sendMessage(source);
                        return 1;
                    }
                    Server server = SLS.servers.resolve(id);
                    if (server != null) {
                        server.kill().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Killed " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to kill server " + id + " Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    Log.warn("Failed to kill server " + server.getId() + " reason: " + failure.getMessage());
                                    SLS.servers.unRegister(server.getId());
                                }
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

}
