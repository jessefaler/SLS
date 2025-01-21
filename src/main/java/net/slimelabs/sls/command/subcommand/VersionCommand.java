package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.Message.Format;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import java.util.Optional;

/**
 * Retrieves the current version information of sls
 */
public class VersionCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("version")
                // -------- Permission --------
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Optional<PluginContainer> pluginContainer = SLS.PROXY.getPluginManager().getPlugin("sls");
                    if (pluginContainer.isPresent()) {
                        PluginDescription description = pluginContainer.get().getDescription();
                        String version = description.getVersion().orElse("unknown");
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Version: ", NamedTextColor.DARK_AQUA, Format.BOLD)
                                .add(version, NamedTextColor.GOLD)
                                .sendMessage(source);
                    } else {
                        source.sendPlainMessage("SLS Plugin not found.");
                    }
                    return 1;
                });
    }
}
