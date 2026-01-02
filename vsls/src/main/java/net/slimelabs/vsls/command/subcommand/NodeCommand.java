package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.SLSAction;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;
import java.util.concurrent.CompletableFuture;

public class NodeCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("node")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls node", "id"))
                            .sendMessage(source);
                    return 1;
                })
                .then(id());
    }

    private static RequiredArgumentBuilder<CommandSource, String> id() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("id", StringArgumentType.string())
                .suggests((context, builder) -> CompletableFuture.supplyAsync(() -> {
                    for (String id : SLS.api.getAllNodeIds().execute()) {
                        builder.suggest(id);
                    }
                    return builder.build();
                }))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "id");

                    SLS.api.getNode(id).executeAsync(clientNode -> {
                        clientNode.getSystemInformation().executeAsync(systemInformation -> {
                            if (systemInformation == null) {
                                ProtoMessage.chat()
                                        .add(MessagePreset.SLS)
                                        .add("System information is not available (null response)", NamedTextColor.RED)
                                        .sendMessage(source);
                                return;
                            }

                            StringBuilder message = new StringBuilder();
                            message.append("<dark_aqua>Info for node </dark_aqua><red>").append(clientNode.getId()).append("</red>\n");
                            message.append("<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>\n");
                            
                            // Node Information
                            message.append(" <gold>-</gold> <dark_gray>Name:</dark_gray> <red>").append(clientNode.getName()).append("</red>\n");
                            message.append(" <gold>-</gold> <dark_gray>Location:</dark_gray> <red>").append(clientNode.getLocation()).append("</red>\n");
                            message.append(" <gold>-</gold> <dark_gray>Url:</dark_gray> <red>").append(clientNode.getUrl()).append("</red>\n");
                            message.append(" <gold>-</gold> <dark_gray>Version:</dark_gray> <red>").append(systemInformation.getVersion() != null ? systemInformation.getVersion() : "Unknown").append("</red>\n");

                            // System Information
                            message.append(" <dark_aqua>System:</dark_aqua>\n");
                            message.append("   <gold>-</gold> <dark_gray>Architecture:</dark_gray> <red>").append(systemInformation.getArchitecture() != null ? systemInformation.getArchitecture() : "Unknown").append("</red>\n");
                            message.append("   <gold>-</gold> <dark_gray>CPU Threads:</dark_gray> <red>").append(systemInformation.getCpuThreads()).append("</red>\n");
                            message.append("   <gold>-</gold> <dark_gray>Memory:</dark_gray> <red>").append(systemInformation.getMemoryFormattedAuto()).append("</red>\n");
                            message.append("   <gold>-</gold> <dark_gray>Kernel Version:</dark_gray> <red>").append(systemInformation.getKernelVersion() != null ? systemInformation.getKernelVersion() : "Unknown").append("</red>\n");
                            message.append("   <gold>-</gold> <dark_gray>OS:</dark_gray> <red>").append(systemInformation.getOs() != null ? systemInformation.getOs() : "Unknown").append("</red>\n");
                            message.append("   <gold>-</gold> <dark_gray>OS Type:</dark_gray> <red>").append(systemInformation.getOsType() != null ? systemInformation.getOsType() : "Unknown").append("</red>\n");
                            
                            // Docker Information
                            var docker = systemInformation.getDocker();
                            message.append(" <dark_aqua>Docker:</dark_aqua>\n");
                            if (docker != null) {
                                message.append("   <gold>-</gold> <dark_gray>Version:</dark_gray> <red>").append(docker.getVersion() != null ? docker.getVersion() : "Unknown").append("</red>\n");
                                
                                var cgroups = docker.getCgroups();
                                if (cgroups != null) {
                                    message.append("   <gold>-</gold> <dark_gray>Cgroups:</dark_gray>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Driver:</dark_gray> <red>").append(cgroups.getDriver() != null ? cgroups.getDriver() : "Unknown").append("</red>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Version:</dark_gray> <red>").append(cgroups.getVersion() != null ? cgroups.getVersion() : "Unknown").append("</red>\n");
                                }
                                
                                var containers = docker.getContainers();
                                if (containers != null) {
                                    message.append("   <gold>-</gold> <dark_gray>Containers:</dark_gray>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Total:</dark_gray> <red>").append(containers.getTotal()).append("</red>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Running:</dark_gray> <green>").append(containers.getRunning()).append("</green>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Paused:</dark_gray> <yellow>").append(containers.getPaused()).append("</yellow>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Stopped:</dark_gray> <red>").append(containers.getStopped()).append("</red>\n");
                                }
                                
                                var storage = docker.getStorage();
                                if (storage != null) {
                                    message.append("   <gold>-</gold> <dark_gray>Storage:</dark_gray>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Driver:</dark_gray> <red>").append(storage.getDriver() != null ? storage.getDriver() : "Unknown").append("</red>\n");
                                    message.append("     <gold>-</gold> <dark_gray>Filesystem:</dark_gray> <red>").append(storage.getFilesystem() != null ? storage.getFilesystem() : "Unknown").append("</red>\n");
                                }
                                
                                var runc = docker.getRunc();
                                if (runc != null) {
                                    message.append("   <gold>-</gold> <dark_gray>Runc Version:</dark_gray> <red>").append(runc.getVersion() != null ? runc.getVersion() : "Unknown").append("</red>\n");
                                }
                            } else {
                                message.append("   <red>Docker information not available</red>\n");
                            }
                            
                            message.append("<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>");
                            
                            ProtoMessage.chat().addMiniMessage(message.toString()).sendMessage(source);
                        }, failure -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Failed to get system information for node " + id + " reason: " + failure.getMessage(), NamedTextColor.RED)
                                    .sendMessage(source);
                        });
                    }, failure -> {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Failed to get node " + id + " reason: " + failure.getMessage(), NamedTextColor.RED)
                                .sendMessage(source);
                    });
                    return 0;
                });
    }

}
