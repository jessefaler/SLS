package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.entites.ClientServer;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class StartCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("start")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls start","blueprint"))
                            .sendMessage(source);
                    return 1;
                })
                .then(type());
    }

    // /sls start
    private static RequiredArgumentBuilder<CommandSource, String> type() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("type", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.blueprints.getTypes().stream().toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String type = StringArgumentType.getString(context, "type");
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls start " + type, "world"))
                            .sendMessage(source);
                    return 0;
                })
                .then(blueprint());
    }

    private static RequiredArgumentBuilder<CommandSource, String> blueprint() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("blueprint", StringArgumentType.string())
                .suggests((context, builder) -> {
                    String type = StringArgumentType.getString(context, "type");
                    SLS.blueprints.getIds(type).stream().toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String type = StringArgumentType.getString(context, "type");
                    String blueprint = StringArgumentType.getString(context, "blueprint");

                    SLS.servers.CreateServer(blueprint).executeAsync(server -> {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Starting " + blueprint, NamedTextColor.GREEN)
                                .add(" (", NamedTextColor.GRAY)
                                .add(server.id, NamedTextColor.DARK_GRAY)
                                .add(")", NamedTextColor.GRAY)
                                .sendMessage(source);
                    }, failure -> {
                        ProtoMessage.chat()
                                .add("Failed to start server. Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                .sendMessage(source);
                    });

                    return 0;
                });
    }
}
