package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.DataType;
import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class StatsCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("stats")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls stats", "id"))
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
                        } else {
                            statusColor = "<red>";
                            statusColorClose = "</red>";
                        }
                        
                        ProtoMessage.chat().addMiniMessage("<dark_aqua>Stats</dark_aqua> <dark_gray>(</dark_gray><dark_aqua>" + server.id + "</dark_aqua><dark_gray>)</dark_gray>:\n" +
                                "<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－\n</st></b></dark_gray>" +
                                " <gold>-</gold> <dark_gray>Status:</dark_gray> " + statusColor + state.getStatus() + statusColorClose + "\n" +
                                " <gold>-</gold> <dark_gray>Cpu:</dark_gray><red> " + stats.getCpuFormatted() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>Mem:</dark_gray> <red>" + stats.getMemoryFormattedAuto() + "</red> <dark_gray>/</dark_gray> <red>" + stats.getMaxMemoryFormattedAuto() + "</red> <dark_gray>(</dark_gray><red>" + stats.getMemoryUsagePercentageFormatted() + "</red><dark_gray>)</dark_gray>\n" +
                                " <gold>-</gold> <dark_gray>Network Inbound:</dark_gray> <red>" + stats.getNetworkIngressFormattedAuto() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>Network Outbound:</dark_gray> <red>" + stats.getNetworkEgressFormattedAuto() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>Uptime:</dark_gray> <red>" + stats.formatUptime() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>Disk (Logical):</dark_gray> <red>" + stats.getDiskFormattedAuto() + "</red> <dark_gray>/</dark_gray> <red>" + stats.getMaxDiskFormattedAuto() + "</red> <dark_gray>(</dark_gray><red>" + stats.getDiskUsagePercentageFormatted() + "</red><dark_gray>)</dark_gray>\n" +
                                " <gold>-</gold> <dark_gray>Disk (Physical):</dark_gray> <red>" + stats.getOverlayFormattedAuto() + "</red>" +
                                "<dark_gray><b><st>\n－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>").sendMessage(source);
                    }, failure -> {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Failed to fetch stats from remote api. Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                .sendMessage(source);
                    });
                    return 0;
                });
    }

}
