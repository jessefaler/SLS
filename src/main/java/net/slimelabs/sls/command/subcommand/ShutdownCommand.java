package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.StringUtils;

public class ShutdownCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("shutdown")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Message.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    Message.chat()
                            .add(MessageFormatter.usage("/sls shutdown", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    // /sls shutdown
    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.SERVER_REGISTRY.getServerNames().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String serverName = StringArgumentType.getString(context, "server");
                    // Shutdown server
                    if(SLS.SERVER_REGISTRY.containsServer(serverName)) {
                        SLS.SERVER_REGISTRY.shutdownServer(serverName);
                        Message.chat()
                                .add(MessagePreset.SLS)
                                .add("Shutdown " + serverName.replace("_", " "), NamedTextColor.GRAY)
                                .sendMessage(source);
                        return 1;
                    }
                    // Server exists but is not running
                    if(SLS.REGISTRY_MANAGER.doseWorldExist(StringUtils.removeTextAfterPeriod(serverName))) {
                        Message.chat()
                                .add(MessagePreset.SLS)
                                .add(serverName.replace("_", " ") + " is not running.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    // No such server exists
                    Message.chat()
                            .add(MessagePreset.SLS)
                            .add("No such server " + serverName, NamedTextColor.RED)
                            .sendMessage(source);
                    return 0;
                });
    }
}
