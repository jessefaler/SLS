package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.io.ServerData;
import net.slimelabs.sls.server.Flags;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.server.core.ServerInstance;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.slimelabs.sls.api.API.getAllServerNames;

public class FlagsCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("flags")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls flags", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    // /sls shutdown
    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    CompletableFuture<Suggestions> future = new CompletableFuture<>();
                    // Handle error
                    getAllServerNames().executeAsync(serverNames -> {
                        for (String name : serverNames) {
                            builder.suggest(name);
                        }
                        future.complete(builder.build());
                    }, future::completeExceptionally);
                    return future;
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String server = StringArgumentType.getString(context, "server");
                    getAllServerNames().executeAsync(serverNames -> {
                        if(!serverNames.contains(server)) {
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Server ", NamedTextColor.RED).add(server, NamedTextColor.DARK_RED).add(" was not found.", NamedTextColor.RED).sendMessage(source);
                            return;
                        }
                        ServerInstance serverInstance = SLS.SERVER_REGISTRY.getServer(server);
                        if(serverInstance == null || serverInstance.flags == null) { // There is no server in the server registry by the given name check ServerData instead
                            String flags = ServerData.getServerFlags(server);
                            if(flags == null) { // There was no entry in the ServerData check the servers an original ServerConfiguration file
                                for(String registryName : SLS.REGISTRY_MANAGER.getRegistryNames()) {
                                   Flags flagsFromConfig = SLS.REGISTRY_MANAGER.getRegistry(registryName).getWorld(StringUtils.truncateAtPeriod(server)).flags;
                                   if(flagsFromConfig != null) {
                                       flags = flagsFromConfig.getFlagsAsString();
                                       ProtoMessage.chat().add("Flags for ", NamedTextColor.GRAY).add(server, NamedTextColor.DARK_AQUA).add(":", NamedTextColor.GRAY).sendMessage(source);
                                       String[] flagList = flags.split(",");
                                       for(String flag : flagList) {
                                           ProtoMessage.chat().add(" - ", NamedTextColor.DARK_GRAY).add(flag, NamedTextColor.GRAY).sendMessage(source);
                                       }
                                       return;
                                   }
                                }
                                ProtoMessage.chat().add(MessagePreset.SLS).add("No flags set for ", NamedTextColor.GRAY).add(server, NamedTextColor.GOLD).sendMessage(source);
                                return;
                            }
                            ProtoMessage.chat().add("Flags for ", NamedTextColor.GRAY).add(server, NamedTextColor.DARK_AQUA).add(":", NamedTextColor.GRAY).sendMessage(source);
                            String[] flagList = flags.split(",");
                            for(String flag : flagList) {
                                ProtoMessage.chat().add(" - ", NamedTextColor.DARK_GRAY).add(flag, NamedTextColor.GRAY).sendMessage(source);
                            }
                            return;
                        }
                        ProtoMessage.chat().add("Flags for ", NamedTextColor.GRAY).add(server, NamedTextColor.DARK_AQUA).add(":", NamedTextColor.GRAY).sendMessage(source);
                        String[] flagList = serverInstance.flags.getFlagsAsString().split(",");
                        for(String flag : flagList) {
                            ProtoMessage.chat().add(" - ", NamedTextColor.DARK_GRAY).add(flag, NamedTextColor.GRAY).sendMessage(source);
                        }
                    });
                    return 1;
                }).then(flags());
    }


    private static final Map<String, String> VALID_FLAGS = Map.of(
            "--save=", "Enable or disable saving",
            "--ram=", "Amount of ram to give the server",
            "--players=", "Set the maximum player count",
            "--view-distance=", "Set the server's view distance",
            "--monitor", "Enable player monitoring"
    );

    // /sls flags <server> --flags
    private static RequiredArgumentBuilder<CommandSource, String> flags() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("flags", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    // Get the current input
                    String input = "";
                    try {
                        input = StringArgumentType.getString(context, "flags").trim();
                    } catch (IllegalArgumentException ignored) {}

                    // Only suggest flags if input contains a dash
                    // Split input into individual flags
                    List<String> enteredFlags = Arrays.asList(input.split("\\s+"));

                    // Suggest flags that have not been entered yet
                    VALID_FLAGS.keySet().stream()
                            .filter(flag -> enteredFlags.stream().noneMatch(inputFlag -> {
                                // Check if the flag is either fully specified with a value or partially matches
                                return inputFlag.equals(flag) || inputFlag.startsWith(flag);
                            }))
                            .forEach(builder::suggest);

                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String flagString = StringArgumentType.getString(context, "flags").trim();
                    String[] parsedFlags = flagString.split("\\s+");
                    Flags flags = new Flags();
                    for (String flag : parsedFlags) {
                        String[] parts = flag.split("=", 2); // Split into two parts: base flag and value
                        String baseFlag = parts[0] + "="; // Extract the base flag
                        String value = parts.length > 1 ? parts[1] : ""; // Extract the value, or empty if none

                        switch (baseFlag) {
                            case "--save=":
                                flags.SAVE = Boolean.parseBoolean(value);
                                break;
                            case "--ram=":
                                flags.RAM = value;
                                break;
                            case "--players=":
                                flags.PLAYERS = Integer.parseInt(value);
                                break;
                            case "--view-distance=":
                                flags.VIEW_DISTANCE = Integer.parseInt(value);
                                break;
                            default:
                                ProtoMessage.chat().add("Unknown flag: ", NamedTextColor.RED).add(baseFlag, NamedTextColor.DARK_RED);
                        }
                    }
                    String server = StringArgumentType.getString(context, "server");
                    ServerInstance serverInstance = SLS.SERVER_REGISTRY.getServer(server);
                    if(serverInstance != null) {
                        serverInstance.flags = flags.mergeFlags(flags, serverInstance.flags);
                    }
                    String readFlags = ServerData.getServerFlags(server);
                    if(readFlags != null) {
                        Flags readFlagsObj = new Flags();
                        readFlagsObj.parseFlagsFromString(readFlags);
                        readFlagsObj = readFlagsObj.mergeFlags(flags, readFlagsObj);
                        ServerData.insertServerFlags(server, readFlagsObj.getFlagsAsString());
                    } else {
                        ServerData.insertServerFlags(server, flags.getFlagsAsString());
                    }
                    ProtoMessage.chat().add(MessagePreset.SLS).add("Updated flags for " + server, NamedTextColor.GRAY).sendMessage(source);
                    ProtoMessage.chat().add("Flags for ", NamedTextColor.GRAY).add(server, NamedTextColor.DARK_AQUA).add(":", NamedTextColor.GRAY).sendMessage(source);
                    String[] flagList = flags.getFlagsAsString().split(",");
                    for(String flag : flagList) {
                        if(flagString.contains(flag)) {
                            ProtoMessage.chat().add(" - ", NamedTextColor.DARK_GRAY).add(flag, NamedTextColor.GREEN).sendMessage(source);
                            continue;
                        }
                        ProtoMessage.chat().add(" - ", NamedTextColor.DARK_GRAY).add(flag, NamedTextColor.GRAY).sendMessage(source);
                    }
                    return 1;
                });
    }
}
