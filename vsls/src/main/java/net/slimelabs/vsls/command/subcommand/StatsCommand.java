package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.DataType;
import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class StatsCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("stats")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if(!(source instanceof Player)) {
                        Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                        return 0;
                    }
                    Server server = ServerUtils.getServer((Player) source);
                    if(server == null) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Server " + ServerUtils.getServerName((Player) source) + " is not an SLS server", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    showStats(server, source);
                    return 1;
                })
                .then(id());
    }

    private static RequiredArgumentBuilder<CommandSource, String> id() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("id", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "id");
                    Server server = SLS.servers.resolve(id);
                    if(server == null) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    showStats(server, source);
                    return 0;
                });
    }

    private static void showStats(Server server, CommandSource source) {
        server.getStats(true).executeAsync(stats -> {
            ServerStatus state = stats.getState();
            String statusColor;
            String statusColorClose;
            if (state == ServerStatus.RUNNING) {
                statusColor = "<green>";
                statusColorClose = "</green>";
            } else if (state == ServerStatus.STARTING) {
                statusColor = "<yellow>";
                statusColorClose = "</yellow>";
            } else if (state == ServerStatus.PAUSED) {
                statusColor = "<aqua>";
                statusColorClose = "</aqua>";
            } else {
                statusColor = "<red>";
                statusColorClose = "</red>";
            }

            ProtoMessage.chat().addMiniMessage("<dark_aqua>Stats</dark_aqua> <dark_gray>(</dark_gray><dark_aqua>" + server.getShortId() + "</dark_aqua><dark_gray>)</dark_gray>:\n" +
                    "<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－\n</st></b></dark_gray>" +
                    " <gold>-</gold> <dark_gray>Status:</dark_gray> " + statusColor + state.getStatus() + statusColorClose + "\n" +
                    " <gold>-</gold> <dark_gray>Cpu:</dark_gray><red> " + stats.getCpuFormatted() + "</red><dark_gray> / </dark_gray><red>" + stats.getCpuLimitFormatted() + "</red>\n" +
                    " <gold>-</gold> <dark_gray>Mem:</dark_gray> <red>" + stats.getMemoryFormattedAuto() + "</red> <dark_gray>/</dark_gray> <red>" + stats.getMaxMemoryFormattedAuto() + "</red> <dark_gray>(</dark_gray><red>" + stats.getMemoryUsagePercentageFormatted() + "</red><dark_gray>)</dark_gray>\n" +
                    " <gold>-</gold> <dark_gray>Network Inbound:</dark_gray> <red>" + stats.getNetworkIngressFormattedAuto() + "</red>\n" +
                    " <gold>-</gold> <dark_gray>Network Outbound:</dark_gray> <red>" + stats.getNetworkEgressFormattedAuto() + "</red>\n" +
                    " <gold>-</gold> <dark_gray>Uptime:</dark_gray> <red>" + stats.formatUptime() + "</red>\n" +
                    " <gold>-</gold> <dark_gray>Disk (Logical):</dark_gray> <red>" + stats.getDiskFormattedAuto() + "</red> <dark_gray>/</dark_gray> <red>" + stats.getMaxDiskFormattedAuto() + "</red> <dark_gray>(</dark_gray><red>" + stats.getDiskUsagePercentageFormatted() + "</red><dark_gray>)</dark_gray>\n" +
                    " <gold>-</gold> <dark_gray>Disk (Physical):</dark_gray> <red>" + stats.getOverlayFormattedAuto() + "</red>" +
                    "<dark_gray><b><st>\n－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>").sendMessage(source);
        }, failure -> Log.requestError("Failed to fetch server stats for " + server.getShortId(), failure, source));
    }

}
