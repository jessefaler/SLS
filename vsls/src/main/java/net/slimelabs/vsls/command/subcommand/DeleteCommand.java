package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.protoxon.S4J.client.entites.ClientServer;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DeleteCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("delete")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls delete", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("all");
                    SLS.servers.getIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    if(id.equals("all")) {
                        SLS.servers.getAll().forEach(server ->
                                server.delete().executeAsync(
                                        success -> {},
                                        failure -> Log.warn("Failed to delete server " + server.getId() + " reason: " + failure.getMessage())
                                )
                        );
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Deleting all servers.", NamedTextColor.GRAY)
                                .sendMessage(source);
                        return 1;
                    }
                    Server server = SLS.servers.getServer(id);
                    if (server != null) {
                        server.delete().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Starting " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to start server " + id + " Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    Log.warn("Failed to start server " + server.getId() + " reason: " + failure.getMessage());
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
                                        failure -> Log.warn("Failed to delete server " + server.getId() + " reason: " + failure.getMessage())
                                )
                        );
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Deleting all servers.", NamedTextColor.GRAY)
                                .sendMessage(source);
                        return 1;
                    }
                    Server server = SLS.servers.getServer(id);
                    if (server != null) {
                        server.delete(true).executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Starting " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to start server " + id + " Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    Log.warn("Failed to start server " + server.getId() + " reason: " + failure.getMessage());
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
