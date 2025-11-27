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

public class KillCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("kill")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls kill", "server"))
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
                    Server server = SLS.servers.getServer(id);
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
                });
    }

}
