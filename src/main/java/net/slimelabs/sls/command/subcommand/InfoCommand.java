package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.server.core.ServerInstance;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class InfoCommand {
    private static final Logger log = LoggerFactory.getLogger(InfoCommand.class);

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("info")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if(SLS.SERVER_REGISTRY.isRegistryEmpty()) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("No Servers are currently online.", NamedTextColor.RED).sendMessage(source);
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.SLS).add("Online Minigames:", NamedTextColor.DARK_AQUA).sendMessage(source);
                    int playerCount;
                    for (ServerInstance server : SLS.SERVER_REGISTRY.getAllServers()) {
                        Optional<RegisteredServer> registeredServer = SLS.PROXY.getServer(server.name);
                        if(registeredServer.isPresent()) {
                            Collection<Player> players = registeredServer.get().getPlayersConnected();
                            String playerNames = players.stream()
                                    .map(Player::getUsername) // Extract usernames
                                    .collect(Collectors.joining(", ")); // Join with commas
                            ProtoMessage.chat()
                                    .addMiniMessage("<hover:show_text:'<dark_purple>" + (playerNames.isEmpty() ? "No players" : playerNames) + "</dark_purple>'>"
                                            + "<gray>  - </gray><green>" + server.name.replace("_", " ") + ":</green>"
                                            + "<gray> " + players.size() + " player" + (players.size() == 1 ? "" : "s") + "</gray></hover>")
                                    .sendMessage(source);
                        }
                    }
                    return 1;
                })
                .then(server());
    }

    // /sls info
    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.SERVER_REGISTRY.getServerNames().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String serverName = StringArgumentType.getString(context, "server");
                    // server info
                    if(SLS.SERVER_REGISTRY.containsServer(serverName)) {
                        ServerInstance server = SLS.SERVER_REGISTRY.getServer(serverName);
                        Optional<RegisteredServer> registeredServer = SLS.PROXY.getServer(server.name);
                        if(registeredServer.isPresent()) {
                            Collection<Player> players = registeredServer.get().getPlayersConnected();
                            String playerNames = players.stream()
                                    .map(Player::getUsername) // Extract usernames
                                    .collect(Collectors.joining(", ")); // Join with commas
                            ProtoMessage.chat()
                                    .addMiniMessage("<hover:show_text:'<dark_purple>" + (playerNames.isEmpty() ? "No players" : playerNames) + "</dark_purple>'>"
                                            + "<green>" + server.name.replace("_", " ") + ":</green>"
                                            + "<gray> " + players.size() + " player" + (players.size() == 1 ? "" : "s") + "</gray></hover>")
                                    .sendMessage(source);
                        }
                        return 1;
                    }
                    // Server exists but is not running
                    if(SLS.REGISTRY_MANAGER.doseWorldExist(StringUtils.truncateAtPeriod(serverName))) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add(serverName.replace("_", " ") + " is not running.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    // No such server exists
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("No such server " + serverName, NamedTextColor.RED)
                            .sendMessage(source);
                    return 0;
                });
    }
}
