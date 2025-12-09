package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class ConsoleCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("console")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls console", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.servers.getIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls console" + id, "command"))
                            .sendMessage(source);
                    return 0;
                }).then(command());
    }

    private static RequiredArgumentBuilder<CommandSource, String> command() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("command", StringArgumentType.greedyString())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    String command = StringArgumentType.getString(context, "command");
                    command = command.startsWith("/") ? command.substring(1) : command;
                    command = command.strip();
                    Server server = SLS.servers.getServer(id);
                    if (server != null) {
                        server.sendCommand(command).executeAsync(success -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Command executed successfully", NamedTextColor.GRAY)
                                    .sendMessage(source);
                        }, failure -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Failed to send the command to server " + id + " reason: " + failure.getMessage(), NamedTextColor.RED)
                                    .sendMessage(source);
                        });
                    } else {
                        // No such server exists
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 1;
                });
    }
}
