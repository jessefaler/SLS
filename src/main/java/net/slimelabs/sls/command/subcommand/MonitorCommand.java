package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.StringUtils;

public class MonitorCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("monitor")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Player player = (Player) source;
                    if(SLS.WATCHER_SERVICE.isWatching(player)) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("You are no longer monitoring " + SLS.WATCHER_SERVICE.getWatchingServer(player).replace("_", " ") + ".", NamedTextColor.GRAY)
                                .sendMessage(source);
                        SLS.WATCHER_SERVICE.stopWatching(player);
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls monitor", "server"))
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

                    if(SLS.SERVER_REGISTRY.containsServer(serverName)) {
                        Player player = (Player) source;
                        if(SLS.WATCHER_SERVICE.isWatching(player, serverName)) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("You are no longer monitoring " + SLS.WATCHER_SERVICE.getWatchingServer(player).replace("_", " ") + ".", NamedTextColor.GRAY)
                                    .sendMessage(source);
                            SLS.WATCHER_SERVICE.stopWatching(player);
                            return 1;
                        }
                        SLS.WATCHER_SERVICE.watchServer(player, serverName);
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Monitoring server ", NamedTextColor.DARK_AQUA)
                                .add(serverName.replace("_", " ") + ".", NamedTextColor.GOLD)
                                .sendMessage(source);
                        return 1;
                    }

                    // Server exists but is not running
                    if(SLS.REGISTRY_MANAGER.doseWorldExist(StringUtils.truncateAtPeriod(serverName))) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add(serverName.replace("_", " ") + " is not running.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    // No such server exists
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("No such server " + serverName, NamedTextColor.RED)
                            .sendMessage(source);
                    return 0;
                });
    }
}
