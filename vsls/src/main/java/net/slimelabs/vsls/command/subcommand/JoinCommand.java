package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.matchmaking.metadata.BlueprintMetadataParser;
import net.slimelabs.vsls.matchmaking.metadata.MatchmakingMetadata;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.CommandMessageParts;
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
                            .add(MessageFormatter.commandUsage("/sls join", "type", "player"))
                            .sendMessage(source);
                    return 1;
                })
                .then(joinPlayerServer())
                .then(type());
    }

    private static LiteralArgumentBuilder<CommandSource> joinPlayerServer() {
        return LiteralArgumentBuilder.<CommandSource>literal("player")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls join player", "player"))
                            .sendMessage(source);
                    return 0;
                })
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("target", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            for (Player player : SLS.proxy.getAllPlayers()) {
                                builder.suggest(player.getUsername());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> joinPlayerServer(context, false))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("--force")
                                .requires(source -> source.hasPermission("sls.command.admin"))
                                .executes(context -> joinPlayerServer(context, true))));
    }

    private static int joinPlayerServer(CommandContext<CommandSource> context, boolean force) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            Log.error("You must be a player to join another player's server");
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "target");
        Optional<Player> target = SLS.proxy.getPlayer(targetName);
        if (target.isEmpty()) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<red>Player</red> <dark_aqua>" + CommandMessageParts.text(targetName) + "</dark_aqua> <red>was not found.</red>")
                    .sendMessage(source);
            ProtoMessage.actionBar()
                    .add("Player not found: " + targetName, NamedTextColor.RED)
                    .sendMessage(source);
            return 0;
        }

        Server server = ServerUtils.getServer(target.get());
        if (server == null) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage(CommandMessageParts.player(target.get()) + " <red>is not on a vSLS server.</red> "
                            + "<dark_gray>Current server:</dark_gray> <gray>" + CommandMessageParts.text(ServerUtils.getServerName(target.get())) + "</gray>")
                    .sendMessage(source);
            ProtoMessage.actionBar()
                    .add(target.get().getUsername() + " is not on a vSLS server", NamedTextColor.RED)
                    .sendMessage(source);
            return 0;
        }

        Server currentServer = ServerUtils.getServer(player);
        if (currentServer != null && currentServer.getId().equals(server.getId())) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<gray>You are already with</gray> " + CommandMessageParts.player(target.get())
                            + " <gray>on</gray> " + CommandMessageParts.server(server) + "<gray>.</gray>")
                    .sendMessage(source);
            ProtoMessage.actionBar()
                    .add("Already on " + server.getCompositeId(), NamedTextColor.GRAY)
                    .sendMessage(source);
            return 1;
        }

        int maxPlayers = getMaxPlayers(server);
        if (!force && maxPlayers > 0 && server.getPlayerCount() >= maxPlayers) {
            if (source.hasPermission("sls.command.admin")) {
                String forceCommand = "/sls join player " + target.get().getUsername() + " --force";
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .addMiniMessage("<yellow>Blueprint limit warning:</yellow> "
                                + CommandMessageParts.server(server) + " <gray>is full ("
                                + server.getPlayerCount() + "/" + maxPlayers + ").</gray>\n"
                                + "<dark_gray>Joining anyway may overfill this blueprint's matchmaking limit.</dark_gray> "
                                + "<click:run_command:'" + CommandMessageParts.text(forceCommand) + "'><hover:show_text:'Run " + CommandMessageParts.text(forceCommand)
                                + "'><green>[Join Anyway]</green></hover></click>")
                        .sendMessage(source);
                ProtoMessage.actionBar()
                        .add("Confirm override to join " + server.getCompositeId(), NamedTextColor.YELLOW)
                        .sendMessage(source);
            } else {
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .addMiniMessage("<red>Server full:</red> " + CommandMessageParts.server(server)
                                + " <gray>(" + server.getPlayerCount() + "/" + maxPlayers + ").</gray>")
                        .sendMessage(source);
                ProtoMessage.actionBar()
                        .add("Server full: " + server.getCompositeId(), NamedTextColor.RED)
                        .sendMessage(source);
            }
            return 0;
        }

        ProtoMessage.chat()
                .add(MessagePreset.SLS)
                .addMiniMessage((force ? "<yellow>Force joining</yellow> " : "<green>Joining</green> ")
                        + CommandMessageParts.player(target.get())
                        + " <gray>on</gray> " + CommandMessageParts.server(server) + "<gray>.</gray>")
                .sendMessage(source);
        ProtoMessage.actionBar()
                .add((force ? "Force joining " : "Joining ") + target.get().getUsername() + " on " + server.getCompositeId(), force ? NamedTextColor.YELLOW : NamedTextColor.GREEN)
                .sendMessage(source);
        SLS.joinService.joinServer(player, server.getCompositeId(), force);
        return 1;
    }

    private static int getMaxPlayers(Server server) {
        var blueprint = SLS.blueprints.getBlueprint(server.getBlueprintId());
        if (blueprint == null) return 0;
        MatchmakingMetadata metadata = BlueprintMetadataParser.parse(blueprint);
        return metadata != null ? metadata.maxPlayers() : 0;
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
                    if(!(source instanceof Player player)) {
                        Log.error("You must specify a player when running this command from console");
                        return 0;
                    }
                    String type = StringArgumentType.getString(context, "type");
                    String blueprint = StringArgumentType.getString(context, "blueprint");
                    if (blueprint.contains(".")) {
                        SLS.joinService.joinServer(player, blueprint.split("\\.", 2)[1]);
                    } else {
                        SLS.joinService.joinBlueprint(player, blueprint);
                    }
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

                    if (blueprint.contains(".")) {
                        String serverId = blueprint.split("\\.", 2)[1];
                        if (playerName.equals("all")) {
                            for (Player player : SLS.proxy.getAllPlayers()) {
                                SLS.joinService.joinServer(player, serverId);
                            }
                            return 1;
                        }
                        if (playerName.equals("local")) {
                            Player player = (Player) source;
                            String serverName = player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse(null);
                            for (Player targetPlayer : Objects.requireNonNull(SLS.proxy.getServer(serverName).orElse(null)).getPlayersConnected()) {
                                SLS.joinService.joinServer(targetPlayer, serverId);
                            }
                            return 1;
                        }
                        Optional<Player> player = SLS.proxy.getPlayer(playerName);
                        if (player.isPresent()) {
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Joining " + playerName + " to " + blueprint, NamedTextColor.DARK_AQUA).sendMessage(source);
                            SLS.joinService.joinServer(player.get(), serverId);
                            return 1;
                        }
                    } else {
                        if (playerName.equals("all")) {
                            for (Player player : SLS.proxy.getAllPlayers()) {
                                SLS.joinService.joinBlueprint(player, blueprint);
                            }
                            return 1;
                        }
                        if (playerName.equals("local")) {
                            Player player = (Player) source;
                            String serverName = player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse(null);
                            for (Player targetPlayer : Objects.requireNonNull(SLS.proxy.getServer(serverName).orElse(null)).getPlayersConnected()) {
                                SLS.joinService.joinBlueprint(targetPlayer, blueprint);
                            }
                            return 1;
                        }
                        Optional<Player> player = SLS.proxy.getPlayer(playerName);
                        if (player.isPresent()) {
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Joining " + playerName + " to " + blueprint, NamedTextColor.DARK_AQUA).sendMessage(source);
                            SLS.joinService.joinBlueprint(player.get(), blueprint);
                            return 1;
                        }
                    }
                    ProtoMessage.chat().add(MessagePreset.SLS).add("Player " + playerName + " was not found.", NamedTextColor.RED).sendMessage(source);
                    return 0;
                });
    }
}
