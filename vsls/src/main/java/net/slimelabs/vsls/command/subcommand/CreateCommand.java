package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.utils.Id;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CreateCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("create")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls create","type"))
                            .sendMessage(source);
                    return 1;
                })
                .then(type());
    }

    // /sls start
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
                            .add(MessageFormatter.commandUsage("/sls create " + type, "blueprint"))
                            .sendMessage(source);
                    return 0;
                })
                .then(blueprint());
    }

    private static RequiredArgumentBuilder<CommandSource, String> blueprint() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("blueprint", StringArgumentType.string())
                .suggests((context, builder) -> {
                    String type = StringArgumentType.getString(context, "type");
                    SLS.blueprints.getIds(type).stream().toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String type = StringArgumentType.getString(context, "type");
                    String blueprint = StringArgumentType.getString(context, "blueprint");

                    ServerCreationAction creation = SLS.api.createServer();
                    creation.setBlueprintId(blueprint);

                    SLS.servers.createServer(creation).executeAsync(server -> {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Created " + blueprint, NamedTextColor.GREEN)
                                .add(" (", NamedTextColor.GRAY)
                                .add(server.id, NamedTextColor.DARK_GRAY)
                                .add(")", NamedTextColor.GRAY)
                                .sendMessage(source);
                    }, failure -> {
                        ProtoMessage.chat()
                                .add("Failed to create server. Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                .sendMessage(source);
                    });

                    return 0;
                }).then(overrides());
    }

    // Define the available overrides
    private static final Map<String, String> OVERRIDES = Map.of(
            "--node=", "The id of the node to create the server on",
            "--save=", "Enable or disable saving",
            "--cpu=", "The percentage of CPU that this instance is allowed to consume",
            "--memory=", "The total amount of memory in mebibytes that this server is allowed to use",
            "--swap=", "The amount of additional swap space to be provided to a container instance.",
            "--io_weight=", "The relative weight for IO operations in a container",
            "--disk_space=", "The amount of disk space in megabytes that a server is allowed to use",
            "--threads=", "Sets which CPU threads can be used by the docker instance.",
            "--oom_disabled=", "If true, disables the OOM killer for this container."
    );

    private static RequiredArgumentBuilder<CommandSource, String> overrides() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("overrides", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    // Get the current input
                    String input = "";
                    try {
                        input = StringArgumentType.getString(context, "overrides").trim();
                    } catch (IllegalArgumentException ignored) {
                    }

                    // Only suggest flags if input contains a dash
                    if (input.startsWith("-")) {
                        // Split input into individual overrides
                        List<String> overrides = Arrays.asList(input.split("\\s+"));

                        // Suggest flags that have not been entered yet
                        OVERRIDES.keySet().stream()
                                .filter(flag -> overrides.stream().noneMatch(override -> {
                                    // Check if the flag is either fully specified with a value or partially matches
                                    return override.equals(flag) || override.startsWith(flag);
                                }))
                                .forEach(builder::suggest);

                        // If the user typed --node=, suggest node IDs from the API
                        overrides.stream()
                                .filter(override -> override.startsWith("--node="))
                                .forEach(override -> {
                                    String prefix = override.substring("--node=".length()); // get current partial node ID
                                    List<String> nodeIds = SLS.api.getAllNodeIds().execute();
                                    nodeIds = Id.shortIds(nodeIds, 8);
                                    nodeIds.stream()
                                            .filter(id -> id.startsWith(prefix)) // match partially typed ID
                                            .forEach(id -> builder.suggest("--node=" + id));
                                });

                        // Suggest true/false for --save= and --oom_disabled=
                        overrides.stream()
                                .filter(override -> override.startsWith("--save=") || override.startsWith("--oom_disabled="))
                                .forEach(override -> {
                                    String prefix = override.contains("=") ? override.substring(override.indexOf('=') + 1) : "";
                                    List<String> options = List.of("true", "false");
                                    options.stream()
                                            .filter(opt -> opt.startsWith(prefix))
                                            .forEach(opt -> builder.suggest(override.substring(0, override.indexOf('=') + 1) + opt));
                                });
                    }

                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String type = StringArgumentType.getString(context, "type");
                    String blueprint = StringArgumentType.getString(context, "blueprint");

                    ServerCreationAction creation = SLS.api.createServer();
                    creation.setBlueprintId(blueprint);

                    // Set overrides if present
                    String[] overrides = StringArgumentType.getString(context, "overrides").trim().split("\\s+");
                    String nodeValue = null;

                    for (String override : overrides) {
                        String[] parts = override.split("=", 2); // Split into two parts: base key and value
                        String key = parts[0] + "="; // Extract the key
                        String value = parts.length > 1 ? parts[1] : ""; // Extract the value, or empty if none

                        switch (key) {
                            case "--node=":
                                nodeValue = value; // Store node value to process asynchronously later
                                break;
                            case "--save=":
                                creation.setSave(Boolean.valueOf(value));
                                break;
                            case "--cpu=":
                                try {
                                    creation.setCpuLimit(Long.valueOf(value));
                                } catch (NumberFormatException e) {
                                    ProtoMessage.chat()
                                            .add("Invalid CPU value: " + value, NamedTextColor.RED)
                                            .sendMessage(source);
                                    return 0;
                                }
                                break;
                            case "--memory=":
                                try {
                                    creation.setMemoryLimit(Long.valueOf(value));
                                } catch (NumberFormatException e) {
                                    ProtoMessage.chat()
                                            .add("Invalid memory value: " + value, NamedTextColor.RED)
                                            .sendMessage(source);
                                    return 0;
                                }
                                break;
                            case "--swap=":
                                try {
                                    creation.setSwap(Long.valueOf(value));
                                } catch (NumberFormatException e) {
                                    ProtoMessage.chat()
                                            .add("Invalid swap value: " + value, NamedTextColor.RED)
                                            .sendMessage(source);
                                    return 0;
                                }
                                break;
                            case "--io_weight=":
                                try {
                                    creation.setIoWeight(Integer.valueOf(value));
                                } catch (NumberFormatException e) {
                                    ProtoMessage.chat()
                                            .add("Invalid IO weight: " + value, NamedTextColor.RED)
                                            .sendMessage(source);
                                    return 0;
                                }
                                break;
                            case "--disk_space=":
                                try {
                                    creation.setDiskSpace(Long.valueOf(value));
                                } catch (NumberFormatException e) {
                                    ProtoMessage.chat()
                                            .add("Invalid disk space: " + value, NamedTextColor.RED)
                                            .sendMessage(source);
                                    return 0;
                                }
                                break;
                            case "--threads=":
                                creation.setThreads(value);
                                break;
                            case "--oom_disabled=":
                                creation.setOomDisabled(Boolean.valueOf(value));
                                break;
                            default:
                                ProtoMessage.chat()
                                        .add("Unknown flag: ", NamedTextColor.RED)
                                        .add(key, NamedTextColor.DARK_RED)
                                        .sendMessage(source);
                                return 0;
                        }
                    }

                    // Helper method to create the server
                    Runnable createServer = () -> {
                        SLS.servers.createServer(creation).executeAsync(server -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Created " + blueprint, NamedTextColor.GREEN)
                                    .add(" (", NamedTextColor.GRAY)
                                    .add(server.id, NamedTextColor.DARK_GRAY)
                                    .add(")", NamedTextColor.GRAY)
                                    .sendMessage(source);
                        }, failure -> {
                            ProtoMessage.chat()
                                    .add("Failed to create server. Reason: " + failure.getMessage(), NamedTextColor.GRAY)
                                    .sendMessage(source);
                        });
                    };

                    // If node was specified, fetch node IDs asynchronously and then create server
                    if (nodeValue != null) {
                        String finalNodeValue = nodeValue;
                        SLS.api.getAllNodeIds().executeAsync(nodeIds -> {
                            String nodeId = Id.findFullId(finalNodeValue, nodeIds);
                            creation.setNodeId(nodeId);
                            createServer.run();
                        }, failure -> {
                            ProtoMessage.chat()
                                    .add("Failed to fetch node ids. Reason: " + failure.getMessage(), NamedTextColor.RED)
                                    .sendMessage(source);
                        });
                    } else {
                        // No node specified, create server immediately
                        createServer.run();
                    }

                    return 0;
                });
    }

}