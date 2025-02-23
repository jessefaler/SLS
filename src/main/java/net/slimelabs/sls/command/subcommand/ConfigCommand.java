package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.Message.ProtoMessage;

import java.util.Arrays;

public class ConfigCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("config")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls config", "view", "reload"))
                            .sendMessage(source);
                    return 1;
                })
                .then(option());
    }

    // /sls view/reload
    private static RequiredArgumentBuilder<CommandSource, String> option() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("option", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("view");
                    builder.suggest("reload");
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String option = StringArgumentType.getString(context, "option");
                    if(option.equals("view")) {
                        ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                        ProtoMessage.chat()
                                .add(MessageFormatter.commandUsage("/sls view", SLS.REGISTRY_MANAGER.getRegistryNames()))
                                .sendMessage(source);
                        return 1;
                    }
                    if(option.equals("reload")) {
                        SLS.REGISTRYIO.reloadAllRegistries(); // Load in the registries
                        SLS.REGISTRYIO.checkUnregisteredWorlds(); // Print any unassigned worlds
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Reloaded all registries configuration files.", NamedTextColor.GREEN).sendMessage(source);
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls config", "view", "reload"))
                            .sendMessage(source);
                    return 0;
                })
                .then(registry());
    }

    private static RequiredArgumentBuilder<CommandSource, String> registry() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("registry", StringArgumentType.string())
                .suggests((context, builder) -> {
                    Arrays.stream(SLS.REGISTRY_MANAGER.getRegistryNames()).toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String option = StringArgumentType.getString(context, "option");
                    String registry = StringArgumentType.getString(context, "registry");
                    if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registry)) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Unknown registry \"" + registry + "\"", NamedTextColor.RED).sendMessage(source);
                        return 1;
                    }
                    if(option.equals("view")) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add(registry + " registry configuration data:", NamedTextColor.AQUA).sendMessage(source);
                        for(ServerConfiguration serverConfiguration : SLS.REGISTRY_MANAGER.getRegistry(registry).getWorlds().values()) {
                            printConfigData(serverConfiguration, source);
                        }
                        return 1;
                    }
                    if(option.equals("reload")) {
                        SLS.REGISTRYIO.reloadRegistry(registry);
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Reloaded " + registry + " configuration data.", NamedTextColor.GREEN).sendMessage(source);
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls config", "view", "reload"))
                            .sendMessage(source);
                    return 0;
                })
                .then(world());
    }

    private static RequiredArgumentBuilder<CommandSource, String> world() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("world", StringArgumentType.string())
                .suggests((context, builder) -> {
                    String registry = StringArgumentType.getString(context, "registry");
                    String option = StringArgumentType.getString(context, "option");
                    if(option.equals("view")) {
                        if(SLS.REGISTRY_MANAGER.doseRegistryExist(registry)) {
                            Arrays.stream(SLS.REGISTRY_MANAGER.getRegistry(registry).getWorldNames()).toList().forEach(builder::suggest);
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String option = StringArgumentType.getString(context, "option");
                    String registry = StringArgumentType.getString(context, "registry");
                    if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registry)) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Unknown registry \"" + registry + "\"", NamedTextColor.RED).sendMessage(source);
                        return 1;
                    }
                    if(option.equals("view")) {
                        String world = StringArgumentType.getString(context, "world");
                        if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registry)) {
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Unknown registry \"" + registry + "\"", NamedTextColor.RED).sendMessage(source);
                            return 1;
                        }
                        if(!SLS.REGISTRY_MANAGER.getRegistry(registry).isWorldPresent(world)) {
                            ProtoMessage.chat().add(MessagePreset.SLS).add("No such world " + world + " in " + registry, NamedTextColor.RED).sendMessage(source);
                            return 1;
                        }
                        ProtoMessage.chat().add(MessagePreset.SLS).add("Configuration data for ", NamedTextColor.AQUA).add(world.replace("_", " ") + ": ", NamedTextColor.DARK_AQUA).sendMessage(source);
                        printConfigData(SLS.REGISTRY_MANAGER.getRegistry(registry).getWorld(world), source);
                        return 1;
                    }
                    if(option.equals("reload")) {
                        ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                        ProtoMessage.chat()
                                .add(MessageFormatter.commandUsage("/sls config reload", SLS.REGISTRY_MANAGER.getRegistryNames()))
                                .sendMessage(source);
                        return 1;
                    }
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls config", "view", "reload"))
                            .sendMessage(source);
                    return 0;
                });
    }

    public static void printConfigData(ServerConfiguration serverConfiguration, CommandSource source) {
        ProtoMessage.chat()
                .add("Name: " + serverConfiguration.name, NamedTextColor.GOLD)
                .add("\n  Authors: ", NamedTextColor.GRAY)
                .add((serverConfiguration.authors != null) ? String.join(", ", serverConfiguration.authors) : "N/A", NamedTextColor.DARK_GRAY)
                .add("\n  Folder Name: ", NamedTextColor.GRAY)
                .add(getLastFolder(serverConfiguration.worldFolder), NamedTextColor.DARK_GRAY)
                .add("\n  Minecraft Version: ", NamedTextColor.GRAY)
                .add(serverConfiguration.version, NamedTextColor.DARK_GRAY)
                .add("\n  Server Software: ", NamedTextColor.GRAY)
                .add(serverConfiguration.software, NamedTextColor.DARK_GRAY)
                .add("\n  View Distance: ", NamedTextColor.GRAY)
                .add(String.valueOf(serverConfiguration.viewDistance), NamedTextColor.DARK_GRAY)
                .add("\n  Allowed Versions: ", NamedTextColor.GRAY)
                .add("Not Implemented", NamedTextColor.DARK_GRAY)
                .add("\n  Flags: ", NamedTextColor.GRAY)
                .add(serverConfiguration.flags.getFlagsAsString(), NamedTextColor.DARK_GRAY)
                .sendMessage(source);
    }

    public static String getLastFolder(String path) {
        if (path == null || path.isEmpty()) {
            return "N/A"; // Return a default value if path is null or empty
        }

        path = path.replaceAll("/+$", ""); // Remove trailing slashes

        int lastSlashIndex = path.lastIndexOf('/');
        return (lastSlashIndex != -1) ? path.substring(lastSlashIndex + 1) : path;
    }
 }
