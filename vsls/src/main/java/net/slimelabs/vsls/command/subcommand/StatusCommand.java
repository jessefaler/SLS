package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class StatusCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("status")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls status", "id"))
                            .sendMessage(source);
                    return 1;
                })
                .then(id());
    }

    private static RequiredArgumentBuilder<CommandSource, String> id() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("id", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.servers.getIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "id");

                    Server server = SLS.servers.getServer(id);
                    if(server == null) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Status: ", NamedTextColor.DARK_AQUA)
                            .add(server.status.getStatus(), NamedTextColor.GRAY)
                            .sendMessage(source);
                    return 0;
                })
                .then(remote());
    }

    // Remote fetches the status from sls daemon via the api
    private static RequiredArgumentBuilder<CommandSource, String> remote() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("remote", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("remote");
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "id");

                    Server server = SLS.servers.getServer(id);
                    if(server == null) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    server.getRemoteStatus().executeAsync(serverStatus -> {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Remote status: ", NamedTextColor.DARK_AQUA)
                                .add(serverStatus.getStatus(), NamedTextColor.GRAY)
                                .sendMessage(source);
                    }, failure -> {
                        ProtoMessage.chat()
                                .add("Failed to fetch status from remote api. Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                .sendMessage(source);
                    });
                    return 0;
                });
    }
}
