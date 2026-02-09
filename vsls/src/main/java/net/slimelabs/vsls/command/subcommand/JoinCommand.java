package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.entities.Blueprint;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.routing.Connector;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Objects;
import java.util.Optional;

public class JoinCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("join")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls join", "type"))
                            .sendMessage(source);
                    return 1;
                })
                .then(type());
    }

    private static RequiredArgumentBuilder<CommandSource, String> type() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("type", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.blueprints.getTypes().stream().toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String type = StringArgumentType.getString(context, "type");
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls join " + type, "blueprint"))
                            .sendMessage(source);
                    return 0;
                })
                .then(blueprint());
    }

    private static RequiredArgumentBuilder<CommandSource, String> blueprint() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("blueprint", StringArgumentType.string())
                .suggests((context, builder) -> {
                    String input = builder.getRemaining();
                    if (!input.contains(".")) {
                        // Suggest blueprint id's for the given type
                        String type = StringArgumentType.getString(context, "type");
                        SLS.blueprints.getIds(type).stream().toList().forEach(builder::suggest);
                    } else {
                        // Suggest server id's for the given blueprint
                        String[] parts = input.split("\\.", 2);
                        String blueprintPart = parts[0];
                        String serverPart = parts.length > 1 ? parts[1] : "";
                        SLS.servers.getAll().stream()
                                .filter(s -> s.getBlueprintId().equals(blueprintPart))
                                .map(Server::getShortId)
                                .filter(id -> id.startsWith(serverPart))
                                .forEach(id -> builder.suggest(blueprintPart + "." + id));
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String type = StringArgumentType.getString(context, "type");
                    String blueprint = StringArgumentType.getString(context, "blueprint");
                    Connector.join((Player) source, blueprint);

                    return 0;
                }).then(player());
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
                    String type = StringArgumentType.getString(context, "type");
                    String blueprint = StringArgumentType.getString(context, "blueprint");
                    String playerName = StringArgumentType.getString(context, "player");

                    if(playerName.equals("all")) {
                        for (Player player : SLS.proxy.getAllPlayers()) {
                            Connector.join(player, blueprint);
                        }
                        return 1;
                    }

                    if(playerName.equals("local")) {
                        Player player = (Player) source;
                        String serverName = player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse(null);
                        for (Player targetPlayer : Objects.requireNonNull(SLS.proxy.getServer(serverName).orElse(null)).getPlayersConnected()) {
                            Connector.join(targetPlayer, blueprint);
                        }
                        return 1;
                    }

                    Optional<Player> player = SLS.proxy.getPlayer(playerName);
                    if(player.isPresent()) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Joining " + playerName + " to " + blueprint, NamedTextColor.DARK_AQUA).sendMessage(source);
                        Connector.join(player.get(), blueprint);
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.SLS).add("Player " + playerName + " was not found.", NamedTextColor.RED).sendMessage(source);
                    return 0;
                });
    }
}
