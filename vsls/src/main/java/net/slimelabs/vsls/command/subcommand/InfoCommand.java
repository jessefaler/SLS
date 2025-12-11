package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.stream.Collectors;

public class InfoCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("info")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage message = ProtoMessage.chat();
                    message.addMiniMessage("<dark_gray><b><st>－－－－－</st></b> INFO <b><st>－－－－－\n</st></b></dark_gray>");
                    if(SLS.servers.getAll().isEmpty()) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("No servers are currently online.", NamedTextColor.RED).sendMessage(source);
                        return 1;
                    }

                    for(Server server : SLS.servers.getAll()) {
                        message.add(" - ", NamedTextColor.GOLD);
                        NamedTextColor color = NamedTextColor.YELLOW;
                        if(server.status == ServerStatus.RUNNING) {
                            color = NamedTextColor.GREEN;
                        } else if (server.status == ServerStatus.STOPPING) {
                            color = NamedTextColor.RED;
                        } else if (server.status == ServerStatus.OFFLINE) {
                            color = NamedTextColor.DARK_RED;
                        }
                        message.add(server.id, color);
                        message.add(": ", NamedTextColor.WHITE);
                        int count = server.getPlayerCount();
                        message.addMiniMessage("<dark_aqua><hover:show_text:'<dark_purple>" + getPlayers(server) + "</dark_purple>'>" + count + "</hover></dark_aqua>");
                        if(count == 1) {
                            message.addMiniMessage("<dark_aqua><hover:show_text:'<dark_purple>" + getPlayers(server) + "</dark_purple>'> player</hover></dark_aqua>");
                        } else {
                            message.addMiniMessage("<dark_aqua><hover:show_text:'<dark_purple>" + getPlayers(server) + "</dark_purple>'> players</hover></dark_aqua>");
                        }
                        message.add("\n");
                    }
                    message.addMiniMessage("<dark_gray><b><st>－－－－－－－－－－－－－</st></b></dark_gray>");
                    message.sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .requires(source -> source.hasPermission("sls.command.admin"))
                .suggests((context, builder) -> {
                    SLS.servers.getIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    // Shutdown server
                    Server server = SLS.servers.getServer(id);
                    if (server != null) {
                        server.getStats().executeAsync(stats -> {
                            ProtoMessage.chat().addMiniMessage("<dark_aqua>Info</dark_aqua> <dark_gray>(</dark_gray><dark_aqua>" + server.id + "</dark_aqua><dark_gray>)</dark_gray>:\n" +
                                    "<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－\n</st></b></dark_gray>" +
                                    " <hover:show_text:'<dark_purple>" + getPlayers(server) + "</dark_purple>'><gold>-</gold> <dark_gray>Players:</dark_gray> <red>" + server.getPlayerCount() + "</red></hover>\n" +
                                    " <gold>-</gold> <dark_gray>Status:</dark_gray> <green>" + server.status.getStatus() + "</green>\n" +
                                    " <gold>-</gold> <dark_gray>Blueprint:</dark_gray><blue> " + server.blueprintId + "</blue>\n" +
                                    " <gold>-</gold> <dark_gray>Type:</dark_gray><blue> " + SLS.blueprints.getBlueprint(server.blueprintId).getType() + "</blue>\n" +
                                    " <gold>-</gold> <dark_gray>Stats:</dark_gray>\n" +
                                    "   <gold>-</gold> <dark_gray>Cpu:</dark_gray><red> " + stats.getCpuFormatted() + "</red>\n" +
                                    "   <gold>-</gold> <dark_gray>Mem:</dark_gray> <red>" + stats.getMemoryFormattedAuto() + "</red> <dark_gray>/</dark_gray> <red>" + stats.getMaxMemoryFormattedAuto() + "</red> <dark_gray>(</dark_gray><red>" + stats.getMemoryUsagePercentageFormatted() + "</red><dark_gray>)</dark_gray>\n" +
                                    "   <gold>-</gold> <dark_gray>Network Inbound:</dark_gray> <red>" + stats.getNetworkIngressFormattedAuto() + "</red>\n" +
                                    "   <gold>-</gold> <dark_gray>Network Outbound:</dark_gray> <red>" + stats.getNetworkEgressFormattedAuto() + "</red>\n" +
                                    "   <gold>-</gold> <dark_gray>Uptime:</dark_gray> <red>" + stats.formatUptime() +
                                    "</red><dark_gray><b><st>\n－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>").sendMessage(source);
                        },  failure -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Failed to fetch server stats reason: " + failure.getMessage(), NamedTextColor.RED)
                                    .sendMessage(source);
                        });
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

    public static String getPlayers(Server server) {
        return SLS.proxy.getServer(server.id)
                .map(rs -> rs.getPlayersConnected().stream()
                        .map(Player::getUsername)
                        .collect(Collectors.joining(", ")))
                .orElse("");
    }

}
