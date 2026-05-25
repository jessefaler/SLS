package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.entities.Blueprint;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class InfoCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("info")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if(!(source instanceof Player)) {
                        Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                        return 0;
                    }
                    Server server = ServerUtils.getServer((Player) source);
                    if (server != null) {
                        showStats(server, source);
                    } else {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Server " + ServerUtils.getServerName((Player) source) + " is not an SLS server", NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 1;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    // Shutdown server
                    Server server = SLS.servers.resolve(id);
                    if (server != null) {
                        showStats(server, source);
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

    public static void showStats(Server server, CommandSource source) {
        server.getStats(true).executeAsync(stats -> {
            Blueprint blueprint = SLS.blueprints.getBlueprint(server.getBlueprintId());
            String type        = blueprint != null ? blueprint.getType() : "Unknown";
            String software    = orUnknown(server.getSoftwareId());
            String version     = orUnknown(server.getVersion() != null ? server.getVersion() : server.getSoftwareVersion());
            String image       = orUnknown(server.getImage());
            String name        = blueprint != null ? blueprint.getName() : server.getBlueprintId();
            String statusColor = "green";
            if(server.getStatus() == ServerStatus.STOPPING || server.getStatus() == ServerStatus.OFFLINE) {
                statusColor = "red";
            } else if(server.getStatus() == ServerStatus.STARTING) {
                statusColor = "yellow";
            } else if(server.getStatus() == ServerStatus.PAUSED) {
                statusColor = "aqua";
            }
            String allocation = (!server.getAllocation().getAlias().isEmpty()
                    ? server.getAllocation().getAlias()
                    : server.getAllocation().getIp())
                    + ":" + server.getAllocation().getPort();
            ProtoMessage.chat().addMiniMessage("<dark_aqua>Info</dark_aqua> <dark_gray>(</dark_gray><dark_aqua>" + server.getCompositeId() + "</dark_aqua><dark_gray>)</dark_gray>:\n" +
                    "<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－\n</st></b></dark_gray>" +
                    " <hover:show_text:'<dark_purple>" + server.getPlayerNames() + "</dark_purple>'><gold>-</gold> <dark_gray>Players:</dark_gray> <red>" + server.getPlayerCount() + "</red></hover>\n" +
                    " <gold>-</gold> <dark_gray>Status:</dark_gray> <" + statusColor + ">" + server.getStatus().getStatus() + "</" + statusColor + ">\n" +
                    " <gold>-</gold> <dark_gray>Blueprint:</dark_gray><hover:show_text:'<dark_purple>" + server.getBlueprintId() + "</dark_purple>'><blue> " + name + "</blue></hover>\n" +
                    " <gold>-</gold> <dark_gray>Type:</dark_gray><blue> " + type + "</blue>\n" +
                    " <hover:show_text:'<dark_purple>Allocation: " + allocation + "\nImage: " + image + "</dark_purple>'><gold>-</gold> <dark_gray>Server:</dark_gray><blue> " + software + " " + version + "</blue></hover>\n" +
                    " <gold>-</gold> <dark_gray>Node:</dark_gray><dark_purple> " + server.getNodeName() + " " + server.getNodeId().substring(0, 8) + "</dark_purple>\n" +
                    " <gold>-</gold> <dark_gray>Stats:</dark_gray> <hover:show_text:'<dark_purple>" +
                    "   <gold>-</gold> <dark_gray>Cpu:</dark_gray><red> " + stats.getCpuFormatted() + "<dark_gray> / </dark_gray><red>" + stats.getCpuLimitFormatted() + "</red>\n" +
                    "   <gold>-</gold> <dark_gray>Mem:</dark_gray> <red>" + stats.getMemoryFormattedAuto() + "</red> <dark_gray>/</dark_gray> <red>" + stats.getMaxMemoryFormattedAuto() + "</red> <dark_gray>(</dark_gray><red>" + stats.getMemoryUsagePercentageFormatted() + "</red><dark_gray>)</dark_gray>\n" +
                    "   <gold>-</gold> <dark_gray>Network Inbound:</dark_gray> <red>" + stats.getNetworkIngressFormattedAuto() + "</red>\n" +
                    "   <gold>-</gold> <dark_gray>Network Outbound:</dark_gray> <red>" + stats.getNetworkEgressFormattedAuto() + "</red>\n" +
                    "   <gold>-</gold> <dark_gray>Uptime:</dark_gray> <red>" + stats.formatUptime() + "</red>\n" +
                    "   <gold>-</gold> <dark_gray>Disk (Logical):</dark_gray> <red>" + stats.getDiskFormattedAuto() + "</red> <dark_gray>/</dark_gray> <red>" + stats.getMaxDiskFormattedAuto() + "</red> <dark_gray>(</dark_gray><red>" + stats.getDiskUsagePercentageFormatted() + "</red><dark_gray>)</dark_gray>\n" +
                    "   <gold>-</gold> <dark_gray>Disk (Physical):</dark_gray> <red>" + stats.getOverlayFormattedAuto() + "</red>" +
                    "</dark_purple>'><dark_gray>[</dark_gray><dark_red>Cpu:</dark_red> <red>" + stats.getCpuFormatted() + "</red><dark_gray>,</dark_gray> <dark_red>Mem:</dark_red> <red>" + stats.getMemoryUsagePercentageFormatted() + "</red><dark_gray>]</dark_gray></hover>\n" +
                    " <gold>-</gold> <dark_gray>Uptime:</dark_gray><red> " + stats.formatUptime() + "</red>\n" +
                    "<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>").sendMessage(source);
        },  failure -> {
            Log.requestError("Failed to fetch server stats for " + server.getCompositeId(), failure, source);
        });
    }

    private static String orUnknown(String value) {
        return value != null && !value.isEmpty() ? value : "Unknown";
    }

}
