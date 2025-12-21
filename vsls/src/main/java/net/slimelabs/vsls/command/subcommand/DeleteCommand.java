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
                    return CompletableFuture.supplyAsync(() -> {
                        for (String id : SLS.api.getAllServerIds().execute()) {
                            builder.suggest(id);
                        }
                        return builder.build();
                    });
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    if(id.equals("all")) {
                        SLS.api.getAllServers().executeAsync(servers -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Deleting all servers.", NamedTextColor.GRAY)
                                    .sendMessage(source);
                            for(ClientServer server : servers) {
                                server.delete().executeAsync(success -> {}, failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to delete server " + server.getId() + " reason: " + failure.getMessage(), NamedTextColor.RED).sendMessage(source);
                                });
                            }
                        }, failure -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Failed to delete all servers. Reason: " + failure.getMessage(), NamedTextColor.RED).sendMessage(source);
                        });
                        return 1;
                    }
                    SLS.api.getServer(id).executeAsync(server -> {
                        server.delete().executeAsync(success -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Deleted " + id, NamedTextColor.GRAY)
                                    .sendMessage(source);
                        }, failure -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Failed to delete server " + id + " Reason: " + failure.getMessage(), NamedTextColor.RED).sendMessage(source);
                        });
                    }, failure -> {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Failed to get server " + id + " Reason: " + failure.getMessage(), NamedTextColor.RED).sendMessage(source);
                    });
                    return 1;
                });
    }

}
