package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.Message.Format;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.StringUtils;

import java.util.*;

public class JoinCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("join")
                .executes(context -> {
                    CommandSource source = context.getSource();


                    Message.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());

                    Message.chat()
                            .add(MessageFormatter.usage("/sls join", SLS.REGISTRY_MANAGER.getRegistryNames()))
                            .sendMessage(context.getSource());
                    return 1;
                })
                .then(registry());
    }

    // /sls join
    private static RequiredArgumentBuilder<CommandSource, String> registry() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("registry", StringArgumentType.string())
                .suggests((context, builder) -> {
                    Arrays.stream(SLS.REGISTRY_MANAGER.getRegistryNames()).toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String registryName = StringArgumentType.getString(context, "registry");
                    Message.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    if (!checkIfRegistryExists(source, registryName)) return 0;
                    Message.chat()
                            .add(MessageFormatter.usage("/sls join " + registryName, "world"))
                            .sendMessage(context.getSource());
                    return 0;
                })
                .then(world());
    }

    // /sls join registry
    private static RequiredArgumentBuilder<CommandSource, String> world() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("world", StringArgumentType.string())
                .suggests((context, builder) -> {
                    CommandSource source = context.getSource();
                    String registryName = StringArgumentType.getString(context, "registry");
                    if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registryName)) return Suggestions.empty();

                    //Checks if the current argument is a valid world and if there is a period it appends the joinable worlds to it
                    String world = null;
                    if (context.getArguments().containsKey("world")) {
                        world = StringArgumentType.getString(context, "world");
                        if(world.contains(".")) {
                            if(SLS.REGISTRY_MANAGER.doseWorldExist(world.replace(".", ""))) {
                                ArrayList<String> joinableWorlds = new ArrayList<>();
                                if (source instanceof Player player) {
                                    String playerName = player.getUsername();
                                    joinableWorlds.add(world + playerName);
                                }
                                for(String joinableWorld : joinableWorlds) {
                                    builder.suggest(joinableWorld);
                                }
                                return builder.buildFuture();
                            }
                        }
                    }

                    SLS.REGISTRY_MANAGER.getRegistry(registryName).getWorlds().keySet().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String registryName = StringArgumentType.getString(context, "registry");
                    if (!checkIfRegistryExists(source, registryName)) return 0;
                    String world = StringArgumentType.getString(context, "world");
                    if(!SLS.REGISTRY_MANAGER.getRegistry(registryName).isWorldPresent(StringUtils.removeTextAfterPeriod(world))) {
                        Message.chat().add(MessagePreset.SLS)
                                .add(" World " + world + " is not present in " + registryName + " registry.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    // The World Is Present So Connect The Player To It.
                    SLS.PLAYER_CONNECTOR.joinServer(world, registryName, source);
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
                    String registryName = StringArgumentType.getString(context, "registry");
                    if (!checkIfRegistryExists(source, registryName)) return 0;
                    String world = StringArgumentType.getString(context, "world");
                    String playerName = StringArgumentType.getString(context, "player");
                    if(!SLS.REGISTRY_MANAGER.getRegistry(registryName).isWorldPresent(StringUtils.removeTextAfterPeriod(world))) {
                        Message.chat().add(MessagePreset.SLS)
                                .add(" World " + world + " is not present in " + registryName + " registry.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    // The World Is Present So Connect The Player To It.
                    if(playerName.equals("all")) { // Connect all players
                        for (Player player : SLS.PROXY.getAllPlayers()) {
                            SLS.PLAYER_CONNECTOR.joinServer(world, registryName, player);
                        }
                    } else if (playerName.equals("local")) { // Connect all players that are on the same server as the executor
                        Player player = (Player) source;
                        String serverName = player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse(null);
                        for (Player targetPlayer : Objects.requireNonNull(SLS.PROXY.getServer(serverName).orElse(null)).getPlayersConnected()) {
                            SLS.PLAYER_CONNECTOR.joinServer(world, registryName, targetPlayer);
                        }
                    } else { // Connect the given player if not null
                        Optional<Player> player = SLS.PROXY.getPlayer(playerName);
                        if(player.isPresent()) {
                            Message.chat().add(MessagePreset.SLS).add("Joining " + playerName + " to " + world.replace("_", " "), NamedTextColor.DARK_AQUA).sendMessage(source);
                            SLS.PLAYER_CONNECTOR.joinServer(world, registryName, player.get());
                            return 1;
                        }
                        Message.chat().add(MessagePreset.SLS).add("Player " + playerName + " was not found.", NamedTextColor.RED).sendMessage(source);
                        return 0;
                    }
                    return 1;
                });
    }


    // -------------- Helper Methods and Common Logic -----------------
    private static boolean checkIfRegistryExists(CommandSource source,String registryName) {
        if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registryName)) {
            Message.chat().add(MessagePreset.SLS)
                    .add(" Unknown registry: ", NamedTextColor.AQUA)
                    .add(registryName, NamedTextColor.GRAY)
                    .sendMessage(source);
            Message.chat()
                    .add(MessageFormatter.usage("/sls join", SLS.REGISTRY_MANAGER.getRegistryNames()))
                    .sendMessage(source);
            return false;
        }
        return true;
    }
}
