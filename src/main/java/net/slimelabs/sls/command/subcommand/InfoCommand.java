package net.slimelabs.sls.command.subcommand;

import com.mattmalec.pterodactyl4j.DataType;
import com.mattmalec.pterodactyl4j.UtilizationState;
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
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("info")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if(SLS.SERVER_REGISTRY.isRegistryEmpty()) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("No servers are currently online.", NamedTextColor.RED).sendMessage(source);
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.SLS).add("Online Servers:", NamedTextColor.DARK_AQUA).sendMessage(source);
                    int playerCount;
                    String stateColor = "red";
                    UtilizationState utilizationState;
                    for (ServerInstance server : SLS.SERVER_REGISTRY.getAllServers()) {
                        Optional<RegisteredServer> registeredServer = SLS.PROXY.getServer(server.name);
                        if(registeredServer.isPresent()) {
                            Collection<Player> players = registeredServer.get().getPlayersConnected();
                            String playerNames = players.stream()
                                    .map(Player::getUsername) // Extract usernames
                                    .collect(Collectors.joining(", ")); // Join with commas
                            if(server.metrics != null) {
                                utilizationState = server.metrics.getState();
                                if (utilizationState == UtilizationState.RUNNING) stateColor = "green";
                                else if(utilizationState == UtilizationState.STARTING) stateColor = "yellow";
                            }
                            ProtoMessage.chat()
                                    .addMiniMessage("<hover:show_text:'<dark_purple>" + (playerNames.isEmpty() ? "No players" : playerNames) + "</dark_purple>'>"
                                            + "<gray>  - </gray><" + stateColor + ">" + server.name.replace("_", " ") + ":</" + stateColor + ">"
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
                            int[] splitCPU = splitDouble(server.metrics.getCPU());
                            String cpu = splitCPU[0] + "<b>.</b>" + splitCPU[1];
                            String stateColor = "red";
                            if(server.metrics == null) {
                                // Server dose not have any metrics (likely just started) display limited info
                                ProtoMessage.chat()
                                        .addMiniMessage("<hover:show_text:'<dark_purple>" + (playerNames.isEmpty() ? "No players" : playerNames) + "</dark_purple>'>"
                                                + "<gray>  - </gray><" + stateColor + ">" + server.name.replace("_", " ") + ":</" + stateColor + ">"
                                                + "<gray> " + players.size() + " player" + (players.size() == 1 ? "" : "s") + "</gray></hover>")
                                        .sendMessage(source);
                                return 1;
                            }
                            UtilizationState utilizationState = server.metrics.getState();
                            if (utilizationState == UtilizationState.RUNNING) stateColor = "green";
                            else if(utilizationState == UtilizationState.STARTING) stateColor = "yellow";
                            ProtoMessage.chat()
                                    .addMiniMessage("<hover:show_text:'<dark_purple>" + (playerNames.isEmpty() ? "No players" : playerNames) + "</dark_purple>'><" + stateColor + ">" + server.name.replace("_", " ") + ":</" + stateColor + ">"
                                    + "\n<dark_gray>  - </dark_gray> <gray>Player Count: </gray><red>" + players.size() + "</red>"
                                    + "\n<dark_gray>  - </dark_gray> <gray>Uptime: </gray><red>" + formatUptime(server.metrics.getUptime()) + "</red>"
                                    + "\n<dark_gray>  - </dark_gray> <gray>CPU: </gray><red>" + cpu + "%</red>"
                                    + "\n<dark_gray>  - </dark_gray> <gray>Memory: </gray><red>" + server.metrics.getMemoryFormatted(DataType.GB) + " <b>/</b> " + server.metrics.getMaxMemoryFormatted(DataType.GB) + "</red>"
                                    + "\n<dark_gray>  - </dark_gray> <gray>Disk: </gray><red>" + server.metrics.getDiskFormatted(DataType.MB) + "</red>"
                                    + "\n<dark_gray>  - </dark_gray> <gray>Network (Inbound) </gray><red>" + server.metrics.getNetworkIngressFormatted(DataType.MB) + "</red>"
                                    + "\n<dark_gray>  - </dark_gray> <gray>Network (Outbound) </gray><red>" + server.metrics.getNetworkEgressFormatted(DataType.MB) + "</red></hover>")
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

    public static String formatUptime(long uptimeMillis) {
        long totalSeconds = uptimeMillis / 1000;  // Convert milliseconds to seconds
        long days = totalSeconds / 86400;         // 86400 seconds in a day
        totalSeconds %= 86400;
        long hours = totalSeconds / 3600;         // 3600 seconds in an hour
        totalSeconds %= 3600;
        long minutes = totalSeconds / 60;         // 60 seconds in a minute
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    public static int[] splitDouble(double value) {
        // Convert the double to a string
        String[] parts = Double.toString(value).split("\\.");

        // Parse the integer and decimal parts
        int integerPart = Integer.parseInt(parts[0]);
        int decimalPart = Integer.parseInt(parts[1]);

        return new int[] { integerPart, decimalPart };
    }
}
