package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Optional;

public class FindCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("find")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls find", "player"))
                            .sendMessage(source);
                    return 0;
                })
                .then(player());
    }

    private static RequiredArgumentBuilder<CommandSource, String> player() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.string())
                .suggests((context, builder) -> {
                    for (Player player : SLS.proxy.getAllPlayers()) {
                        builder.suggest(player.getUsername());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String playerName = StringArgumentType.getString(context, "player");
                    Optional<Player> player = SLS.proxy.getPlayer(playerName);
                    if (player.isEmpty()) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Player " + playerName + " was not found.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }

                    Server server = ServerUtils.getServer(player.get());
                    if (server == null) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Player " + player.get().getUsername() + " is not on an SLS server.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }

                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Player " + player.get().getUsername() + " is currently on " + server.getCompositeId(), NamedTextColor.DARK_AQUA)
                            .sendMessage(source);
                    return 1;
                });
    }
}
