package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class DebugCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("debug")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();

                    if (!(source instanceof Player player)) {
                        ProtoMessage.chat().add(MessagePreset.SLS)
                                .add("This command can only be run by a player.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }

                    // Toggle debug mode
                    if (Log.getDebugPlayers().contains(player.getUniqueId())) {
                        Log.removeDebugPlayer(player);
                        ProtoMessage.chat().add(MessagePreset.SLS)
                                .add("Debug mode disabled.", NamedTextColor.GRAY)
                                .sendMessage(player);
                    } else {
                        Log.addDebugPlayer(player);
                        ProtoMessage.chat().add(MessagePreset.SLS)
                                .add("Debug mode enabled.", NamedTextColor.GRAY)
                                .sendMessage(player);
                    }

                    return 1;
                });
    }

}
