package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class SystemCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("system")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    SLS.api.getSystemInformation().executeAsync(info -> {
                        if (info == null) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("System information is not available (null response)", NamedTextColor.RED)
                                    .sendMessage(source);
                            return;
                        }
                        ProtoMessage.chat().addMiniMessage(
                                "<dark_gray><b><st>－－－－－－－－－</st></b><dark_aqua> INFO </dark_aqua><b><st>－－－－－－－－</st></b></dark_gray>\n" +
                                " <gold>-</gold> <dark_gray>Version:</dark_gray> <red>" + info.getVersion() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>Architecture:</dark_gray> <red>" + info.getArchitecture() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>CPU Threads:</dark_gray> <red>" + info.getCpuThreads() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>Memory:</dark_gray> <red>" + info.getMemoryFormattedAuto() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>Kernel Version:</dark_gray> <red>" + info.getKernelVersion() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>OS:</dark_gray> <red>" + info.getOs() + "</red>\n" +
                                " <gold>-</gold> <dark_gray>OS Type:</dark_gray> <red>" + info.getOsType() + "</red>\n" +
                                "<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>"
                        ).sendMessage(source);
                    }, failure -> {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Failed to get system info: " + failure.getMessage(), NamedTextColor.RED)
                                .sendMessage(source);
                    });

                    return 1;
                });
    }
}
