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

public class RestartCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("restart")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls restart","type"))
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
                    Server server = SLS.servers.getServer(id);
                    if (server != null) {
                        server.restart().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Restarting " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to restart server " + id + " Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    Log.warn("Failed to restart server " + server.getId() + " reason: " + failure.getMessage());
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
