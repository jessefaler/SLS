package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.Message.MessagePreset;

import java.util.Objects;
import java.util.Optional;

public class DequeueCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("dequeue")
                // -------- Permission --------
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Player player = (Player) source;
                    if(SLS.PLAYER_CONNECTOR.activeServices.containsKey(player.getUniqueId())) {
                        SLS.PLAYER_CONNECTOR.dequeuePlayer(player.getUniqueId());
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.SLS).add("You are not in queue.", NamedTextColor.GRAY).sendMessage(source);
                    return 1;
                })
                .then(player());
    }

    // /sls join registry world
    private static RequiredArgumentBuilder<CommandSource, String> player() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.string())
                .requires(source -> source.hasPermission("sls.command.admin"))
                .suggests((context, builder) -> {
                    builder.suggest("all");
                    builder.suggest("local");
                    for(Player player : SLS.PROXY.getAllPlayers()) {
                        builder.suggest(player.getUsername());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String playerName = StringArgumentType.getString(context, "player");
                    if(playerName.equals("all")) { // Connect all players
                        for (Player player : SLS.PROXY.getAllPlayers()) {
                            SLS.PLAYER_CONNECTOR.dequeuePlayer(player.getUniqueId());
                        }
                    } else if (playerName.equals("local")) { // Connect all players that are on the same server as the executor
                        Player player = (Player) source;
                        String serverName = player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse(null);
                        for (Player targetPlayer : Objects.requireNonNull(SLS.PROXY.getServer(serverName).orElse(null)).getPlayersConnected()) {
                            SLS.PLAYER_CONNECTOR.dequeuePlayer(targetPlayer.getUniqueId());
                        }
                    } else { // Connect the given player if not null
                        Optional<Player> player = SLS.PROXY.getPlayer(playerName);
                        if(player.isPresent()) {
                            if(SLS.PLAYER_CONNECTOR.activeServices.containsKey(player.get().getUniqueId())) {
                                SLS.PLAYER_CONNECTOR.dequeuePlayer(player.get().getUniqueId());
                                ProtoMessage.chat().add(MessagePreset.SLS).add("Dequeued " + playerName, NamedTextColor.DARK_AQUA).sendMessage(source);
                                return 1;
                            }
                            ProtoMessage.chat().add(MessagePreset.SLS).add(playerName + " is not in queue.", NamedTextColor.RED).sendMessage(source);
                            return 1;
                        }
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Player " + playerName + " was not found.", NamedTextColor.RED).sendMessage(source);
                        return 0;
                    }
                    return 1;
                });
    }
}
