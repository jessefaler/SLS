package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class DeleteCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("delete")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if(!(source instanceof Player)) {
                        Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                        return 0;
                    }
                    Server server = ServerUtils.getServer((Player) source);
                    if (server != null) {
                        server.delete().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Deleted " + server.getCompositeId(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> Log.requestError("Failed to delete server " + server.getCompositeId(), failure, source)
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
                        SLS.servers.getAll().forEach(server ->
                                server.delete().executeAsync(
                                        success -> {},
                                        failure -> Log.requestError("Failed to delete server " + server.getCompositeId(), failure, source)
                                )
                        );
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Deleting all servers.", NamedTextColor.GRAY)
                                .sendMessage(source);
                        return 1;
                    }
                    Server server = SLS.servers.resolve(id);
                    if (server != null) {
                        server.delete().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Deleted " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> Log.requestError("Failed to delete server " + id, failure, source)
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

    private static RequiredArgumentBuilder<CommandSource, String> force() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("force", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("force");
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    if(id.equals("all")) {
                        SLS.servers.getAll().forEach(server ->
                                server.delete(true).executeAsync(
                                        success -> {},
                                        failure -> Log.requestError("Failed to delete server " + server.getCompositeId(), failure, source)
                                )
                        );
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Deleting all servers.", NamedTextColor.GRAY)
                                .sendMessage(source);
                        return 1;
                    }
                    Server server = SLS.servers.resolve(id);
                    if (server != null) {
                        server.delete(true).executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Deleting " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    Log.requestError("Failed to delete server " + id, failure, source);
                                    SLS.servers.unRegister(server.getId());
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to delete server on the remote node but was successfully unregistered from vSLS " + id, NamedTextColor.RED)
                                            .sendMessage(source);
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
