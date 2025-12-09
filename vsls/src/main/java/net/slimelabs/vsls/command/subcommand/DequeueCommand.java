package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.routing.Queue;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Objects;
import java.util.Optional;

public class DequeueCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("dequeue")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Player player = (Player) source;
                    Queue queue = SLS.queue.getQueue(player);
                    if (queue != null && queue.dequeue(player)) {
                        ProtoMessage.chat().add(MessagePreset.SLS)
                                .add("You have been dequeued from " + queue.server.name, NamedTextColor.RED)
                                .sendMessage(player);
                    } else {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("You are not in queue.", NamedTextColor.GRAY).sendMessage(source);
                    }
                    return 1;
                })
                .then(player());
    }

    private static RequiredArgumentBuilder<CommandSource, String> player() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.string())
                .requires(source -> source.hasPermission("sls.command.admin"))
                .suggests((context, builder) -> {
                    builder.suggest("all");
                    builder.suggest("local");
                    for(Player player : SLS.proxy.getAllPlayers()) {
                        builder.suggest(player.getUsername());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String playerName = StringArgumentType.getString(context, "player");
                    if(playerName.equals("all")) { // Connect all players
                        for (Player player : SLS.proxy.getAllPlayers()) {
                            Queue queue = SLS.queue.getQueue(player);
                            if(queue != null && queue.dequeue(player)) {
                                ProtoMessage.chat().add(MessagePreset.SLS)
                                        .add("You have been dequeued from " + queue.server.name, NamedTextColor.RED)
                                        .sendMessage(player);
                            }
                        }
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Dequeued all players", NamedTextColor.DARK_AQUA).sendMessage(source);
                    } else if (playerName.equals("local")) { // Connect all players that are on the same server as the executor
                        Player player = (Player) source;
                        String serverName = player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse(null);
                        for (Player targetPlayer : Objects.requireNonNull(SLS.proxy.getServer(serverName).orElse(null)).getPlayersConnected()) {
                            Queue queue = SLS.queue.getQueue(targetPlayer);
                            if(queue != null && queue.dequeue(targetPlayer)) {
                                ProtoMessage.chat().add(MessagePreset.SLS)
                                        .add("You have been dequeued from " + queue.server.name, NamedTextColor.RED)
                                        .sendMessage(player);
                            }
                        }
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Dequeued local players", NamedTextColor.DARK_AQUA).sendMessage(source);
                    } else {
                        Optional<Player> player = SLS.proxy.getPlayer(playerName);
                        if(player.isEmpty()) {
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Player " + playerName + " was not found.", NamedTextColor.RED).sendMessage(source);
                            return 0;
                        }
                        Queue queue = SLS.queue.getQueue(player.get());
                        if (queue != null && queue.dequeue(player.get())) {
                            ProtoMessage.chat().add(MessagePreset.SLS)
                                    .add("You have been dequeued from " + queue.server.name, NamedTextColor.RED)
                                    .sendMessage(player.get());
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Dequeued " + playerName, NamedTextColor.DARK_AQUA).sendMessage(source);
                        } else {
                            ProtoMessage.chat().add(MessagePreset.SLS).add(playerName + " is not in queue.", NamedTextColor.RED).sendMessage(source);
                        }
                        return 1;
                    }
                    return 1;
                });
    }
}
