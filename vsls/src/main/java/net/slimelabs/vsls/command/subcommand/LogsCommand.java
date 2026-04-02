package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class LogsCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("logs")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls logs", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("this");
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    Server server;
                    if(id.equals("this")) {
                        if(!(source instanceof Player)) {
                            Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                            return 0;
                        }
                        server = ServerUtils.getServer((Player) source);
                        if(server == null) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Server " + ServerUtils.getServerName((Player) source) + " is not an SLS server", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 0;
                        }
                    } else {
                        server = SLS.servers.resolve(id);
                    }
                    if (server != null) {
                        server.getLogs().executeAsync(logs -> {
                            ComponentBuilder<TextComponent, TextComponent.Builder> builder = Component.text();
                            ProtoMessage.chat().addMiniMessage("<dark_gray><b><st>－－－－</st></b><gold> Logs for " + server.getShortId() + " </gold><b><st>－－－－</st></b></dark_gray>\n").sendMessage(source);
                            for (String log : logs) {
                                builder.append(
                                        Component.text(log + "\n", NamedTextColor.GRAY)
                                );
                            }
                            source.sendMessage(builder.build());
                            ProtoMessage.chat().addMiniMessage("<dark_gray><b><st>－－－－－－－－</st></b><red> END </red><b><st>－－－－－－－－</st></b></dark_gray>").sendMessage(source);
                        }, failure -> Log.requestError("Failed to get logs for server " + id, failure, source));
                    } else {
                        // No such server exists
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 0;
                }).then(lines());
    }

    private static RequiredArgumentBuilder<CommandSource, String> lines() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("lines", StringArgumentType.string())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    String linesString = StringArgumentType.getString(context, "lines");
                    int lines;
                    try {
                        lines = Integer.parseInt(linesString);
                    } catch (NumberFormatException e) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Invalid number " + linesString, NamedTextColor.RED).sendMessage(source);
                        return 0;
                    }
                    Server server;
                    if(id.equals("this")) {
                        if(!(source instanceof Player)) {
                            Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                            return 0;
                        }
                        server = ServerUtils.getServer((Player) source);
                        if(server == null) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Server " + ServerUtils.getServerName((Player) source) + " is not an SLS server", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 0;
                        }
                    } else {
                        server = SLS.servers.resolve(id);
                    }
                    if (server != null) {
                        server.getLogs(lines).executeAsync(logs -> {
                            ComponentBuilder<TextComponent, TextComponent.Builder> builder = Component.text();
                            ProtoMessage.chat().addMiniMessage("<dark_gray><b><st>－－－－</st></b><gold> Logs for " + server.getShortId() + " </gold><b><st>－－－－</st></b></dark_gray>\n").sendMessage(source);
                            for (String log : logs) {
                                builder.append(
                                        Component.text(log + "\n", NamedTextColor.GRAY)
                                );
                            }
                            source.sendMessage(builder.build());
                            ProtoMessage.chat().addMiniMessage("<dark_gray><b><st>－－－－－－－－</st></b><red> END </red><b><st>－－－－－－－－</st></b></dark_gray>").sendMessage(source);
                        }, failure -> Log.requestError("Failed to get logs for server " + id, failure, source));
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
