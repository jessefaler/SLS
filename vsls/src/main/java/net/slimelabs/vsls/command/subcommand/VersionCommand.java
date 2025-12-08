package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.utils.PluginInfo;
import net.slimelabs.vsls.utils.message.Format;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

/**
 * Retrieves the current version information of vsls
 */
public class VersionCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("version")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Version: ", NamedTextColor.DARK_AQUA, Format.BOLD)
                            .add(PluginInfo.getVersion(), NamedTextColor.GOLD)
                            .add(" By: ", NamedTextColor.DARK_AQUA)
                            .add(PluginInfo.getAuthors(), NamedTextColor.GOLD)
                            .sendMessage(source);
                    return 1;
                });
    }
}
