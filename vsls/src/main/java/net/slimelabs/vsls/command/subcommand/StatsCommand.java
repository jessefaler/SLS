package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.DataType;
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

                    server.getStats().executeAsync(stats -> {
                        ProtoMessage.chat()
                                .add("CPU: ", NamedTextColor.DARK_GRAY)
                                .add(stats.getCpuFormatted() + "\n", NamedTextColor.RED)
                                .add("MEM: ", NamedTextColor.DARK_GRAY)
                                .add(stats.getMemoryFormattedAuto(), NamedTextColor.RED)
                                .add(" / ", NamedTextColor.GRAY)
                                .add(stats.getMaxMemoryFormattedAuto(), NamedTextColor.RED)
                                .add(" (", NamedTextColor.GRAY)
                                .add(stats.getMemoryUsagePercentageFormatted(), NamedTextColor.RED)
                                .add(")\n", NamedTextColor.GRAY)
                                .add("Network Inbound: ", NamedTextColor.DARK_GRAY)
                                .add(stats.getNetworkIngressFormattedAuto() + "\n", NamedTextColor.RED)
                                .add("Network Outbound: ", NamedTextColor.DARK_GRAY)
                                .add(stats.getNetworkEgressFormattedAuto() + "\n", NamedTextColor.RED)
                                .add("Uptime: ", NamedTextColor.DARK_GRAY)
                                .add(String.valueOf(stats.formatUptime()), NamedTextColor.RED)
                                .sendMessage(source);
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
