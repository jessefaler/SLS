package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entities.ClientServer;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class PauseCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("pause")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls pause","type"))
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
                        server.pause().executeAsync(
                                success -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Pausing " + id, NamedTextColor.GRAY)
                                            .sendMessage(source);
                                },
                                failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to pause server " + id + " Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                            .sendMessage(source);
                                    Log.warn("Failed to pause server " + server.getId() + " reason: " + failure.getMessage());
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

