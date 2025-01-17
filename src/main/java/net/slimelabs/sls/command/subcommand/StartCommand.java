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
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.server.core.Flags;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StartCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("start")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Message.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    Message.chat()
                            .add(MessageFormatter.usage("/sls start", SLS.REGISTRY_MANAGER.getRegistryNames()))
                            .sendMessage(source);
                    return 1;
                })
                .then(registry());
    }

    // /sls start
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
                            .add(MessageFormatter.usage("/sls start " + registryName, "world"))
                            .sendMessage(source);
                    return 0;
                })
                .then(world());
    }

    // /sls start registry
    private static RequiredArgumentBuilder<CommandSource, String> world() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("world", StringArgumentType.string())
                // ----------------------------------- SUGGEST -----------------------------------
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
                // ----------------------------------- EXECUTE -----------------------------------
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String registryName = StringArgumentType.getString(context, "registry");
                    if (!checkIfRegistryExists(source, registryName)) return 0;
                    String world = StringArgumentType.getString(context, "world");
                    if(!SLS.REGISTRY_MANAGER.getRegistry(registryName).isWorldPresent(StringUtils.removeTextAfterPeriod(world))) {
                        Message.chat().add(MessagePreset.SLS)
                                .add("Error: ", NamedTextColor.RED)
                                .add(world, TextColor.color(131, 192, 255))
                                .add(" is not present in the ", NamedTextColor.RED)
                                .add(registryName, TextColor.color(131, 192, 255))
                                .add(" registry.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    ServerConfiguration serverConfiguration = SLS.REGISTRY_MANAGER.getRegistry(registryName).getWorld(StringUtils.removeTextAfterPeriod(world));
                    if(SLS.SERVER_REGISTRY.containsServer(world)) {
                        Message.chat()
                                .add(MessagePreset.SLS)
                                .add("Server ", NamedTextColor.RED)
                                .addMiniMessage("<gradient:#83C0FF:#0070C0>" + world.replace("_", " ") + "</gradient>")
                                .add(" is already running", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    // Execute the server start on a separate thread so Http Requests don't block the main thread
                    SLS.EXECUTOR.submit(() -> {
                        boolean success = SLS.SERVER_REGISTRY.startServer(world, serverConfiguration, source);
                        if(!success) {
                            Message.chat()
                                    .add("An error occurred while attempting to start the server. Please try again or check the logs for more details.", NamedTextColor.RED)
                                    .sendMessage(source);
                        }
                    });
                    Message.chat()
                            .add(MessagePreset.SLS)
                            .addMiniMessage("<gradient:#58FF7A:#33C0C6>Starting " + world.replace("_", " ") + "...</gradient>")
                            .sendMessage(source);
                    return 1;
                }).then(flags());
    }

    // Define the available flags
    private static final Map<String, String> VALID_FLAGS = Map.of(
            "--save=", "Enable or disable saving",
            "--ram=", "Amount of ram to give the server",
            "--players=", "Set the maximum player count",
            "--view-distance=", "Set the server's view distance",
            "--monitor", "Enable player monitoring"
    );

    // /sls start <registry> <world> --flags
    private static RequiredArgumentBuilder<CommandSource, String> flags() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("flags", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    // Get the current input
                    String input = "";
                    try {
                        input = StringArgumentType.getString(context, "flags").trim();
                    } catch (IllegalArgumentException ignored) {}

                    // Only suggest flags if input contains a dash
                    if (input.startsWith("-")) {
                        // Split input into individual flags
                        List<String> enteredFlags = Arrays.asList(input.split("\\s+"));

                        // Suggest flags that have not been entered yet
                        VALID_FLAGS.keySet().stream()
                                .filter(flag -> enteredFlags.stream().noneMatch(inputFlag -> {
                                    // Check if the flag is either fully specified with a value or partially matches
                                    return inputFlag.equals(flag) || inputFlag.startsWith(flag);
                                }))
                                .forEach(builder::suggest);
                    }

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
                                flags.RAM = Integer.parseInt(value);
                                break;
                            case "--players=":
                                flags.PLAYERS = Integer.parseInt(value);
                                break;
                            case "--view-distance=":
                                flags.VIEW_DISTANCE = Integer.parseInt(value);
                                break;
                            default:
                                Message.chat().add("Unknown flag: ", NamedTextColor.RED).add(baseFlag, NamedTextColor.DARK_RED);
                        }

                        String registryName = StringArgumentType.getString(context, "registry");
                        if (!checkIfRegistryExists(source, registryName)) return 0;
                        String world = StringArgumentType.getString(context, "world");
                        if(!SLS.REGISTRY_MANAGER.getRegistry(registryName).isWorldPresent(StringUtils.removeTextAfterPeriod(world))) {
                            Message.chat().add(MessagePreset.SLS)
                                    .add("Error: ", NamedTextColor.RED)
                                    .add(world, TextColor.color(131, 192, 255))
                                    .add(" is not present in the ", NamedTextColor.RED)
                                    .add(registryName, TextColor.color(131, 192, 255))
                                    .add(" registry.", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 0;
                        }
                        ServerConfiguration serverConfiguration = SLS.REGISTRY_MANAGER.getRegistry(registryName).getWorld(StringUtils.removeTextAfterPeriod(world));
                        if(SLS.SERVER_REGISTRY.containsServer(world)) {
                            Message.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Server ", NamedTextColor.RED)
                                    .addMiniMessage("<gradient:#83C0FF:#0070C0>" + world.replace("_", " ") + "</gradient>")
                                    .add(" is already running", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 0;
                        }
                        // Execute the server start on a separate thread so Http Requests don't block the main thread
                        SLS.EXECUTOR.submit(() -> {
                            boolean success = SLS.SERVER_REGISTRY.startServer(world, serverConfiguration, source, flags);
                            if(!success) {
                                Message.chat()
                                        .add("An error occurred while attempting to start the server. Please try again or check the logs for more details.", NamedTextColor.RED)
                                        .sendMessage(source);
                            }
                        });
                        Message.chat()
                                .add(MessagePreset.SLS)
                                .addMiniMessage("<gradient:#58FF7A:#33C0C6>Starting " + world.replace("_", " ") + "...</gradient>")
                                .sendMessage(source);
                        return 1;
                    }
                    return 1;
                });
    }

    // -------------- Helper Methods and Common Logic -----------------
    private static boolean checkIfRegistryExists(CommandSource source,String registryName) {
        if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registryName)) {
            Message.chat().add(MessagePreset.SLS)
                    .add("Unknown registry: ", NamedTextColor.AQUA)
                    .add(registryName, NamedTextColor.GRAY)
                    .sendMessage(source);
            Message.chat()
                    .add(MessageFormatter.usage("/sls start", SLS.REGISTRY_MANAGER.getRegistryNames()))
                    .sendMessage(source);
            return false;
        }
        return true;
    }
}
