package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.SLSAction;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConsoleCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("console")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls console", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.servers.getIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls console " + id, "command"))
                            .sendMessage(source);
                    return 0;
                }).then(command());
    }

    private static RequiredArgumentBuilder<CommandSource, String> command() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("command", StringArgumentType.greedyString())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    String command = StringArgumentType.getString(context, "command");
                    command = command.startsWith("/") ? command.substring(1) : command;
                    command = command.strip();
                    Server server = SLS.servers.getServer(id);
                    if (server != null) {
                        String finalCommand = command;
                        server.sendCommand(command).executeAsync(success -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Command executed successfully", NamedTextColor.GRAY)
                                    .sendMessage(source);
                            // Wait a bit for the command output to appear in logs
                            SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
                                getCommandOutput(server, finalCommand).executeAsync(output -> {
                                    output.sendMessage(source);
                                }, failure -> {
                                    ProtoMessage.chat()
                                            .add(MessagePreset.SLS)
                                            .add("Failed to get command output reason: " + failure.getMessage(), NamedTextColor.RED)
                                            .sendMessage(source);
                                });
                            }).delay(100, TimeUnit.MILLISECONDS).schedule();
                        }, failure -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Failed to send the command to server " + id + " reason: " + failure.getMessage(), NamedTextColor.RED)
                                    .sendMessage(source);
                        });
                    } else {
                        // No such server exists
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 1;
                });
    }

    /**
     * Attempts to capture the output of a command that was executed on a server's console.
     * It fetches the last 20 log lines, finds the line containing the command, and returns
     * the line immediately following it as the command's output.
     * <p>
     * This may fail if more than 15 log lines are produced between sending the command and
     * fetching the logs, but this is unlikely unless the server is heavily spamming output.
     *
     * @param server  the target server
     * @param command the exact command string that was sent
     * @return mapped action containing the detected command output
     */
    public static SLSAction<ProtoMessage> getCommandOutput(Server server, String command) {
        return server.getLogs(20).map(logs -> {
            // Search backwards through logs to find the command.
            // Commands can appear in two formats:
            // - Legacy servers: ">command" (with ">" prefix)
            // - Newer servers: "command" (without prefix)
            int commandIndex = -1;
            
            for (int i = logs.size() - 1; i >= 0; i--) {
                String line = logs.get(i);
                if (line == null) continue;
                
                String trimmedLine = line.trim();
                boolean matches = false;
                
                // Check legacy format: ">command"
                if (trimmedLine.startsWith(">")) {
                    String afterPrefix = trimmedLine.substring(1).trim();
                    // Check if it matches our command exactly or starts with it (for commands with arguments)
                    if (afterPrefix.equals(command) || afterPrefix.startsWith(command + " ")) {
                        matches = true;
                    }
                } else {
                    // Check newer format: "command" (no prefix)
                    // Make sure it's not a log line (which would start with "[" timestamp)
                    if (!trimmedLine.startsWith("[")) {
                        // Check if it matches our command exactly or starts with it
                        if (trimmedLine.equals(command) || trimmedLine.startsWith(command + " ")) {
                            matches = true;
                        }
                    }
                }
                
                if (matches) {
                    commandIndex = i;
                    break;
                }
            }
            
            if (commandIndex == -1 || commandIndex >= logs.size() - 1) {
                // Command not found or no output after command
                return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                        + server.name
                        + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>No output found</gray>");
            }
            
            // Get the line right after the command
            String outputLine = logs.get(commandIndex + 1);
            if (outputLine == null) {
                return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                        + server.name
                        + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>No output found</gray>");
            }
            
            outputLine = outputLine.trim();
            
            // Remove leading ">" if present (console prompt character)
            if (outputLine.startsWith(">")) {
                outputLine = outputLine.substring(1).trim();
            }
            
            // Skip empty lines or just ">" prompts
            if (outputLine.isEmpty() || outputLine.equals(">")) {
                // Try the next line
                if (commandIndex + 2 < logs.size()) {
                    String nextLine = logs.get(commandIndex + 2);
                    if (nextLine != null) {
                        nextLine = nextLine.trim();
                        // Remove leading ">" if present
                        if (nextLine.startsWith(">")) {
                            nextLine = nextLine.substring(1).trim();
                        }
                        if (!nextLine.isEmpty() && !nextLine.equals(">")) {
                            outputLine = nextLine;
                        } else {
                            return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                                    + server
                                    + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>No output found</gray>");
                        }
                    } else {
                        return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                                + server
                                + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>No output found</gray>");
                    }
                } else {
                    return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                            + server.name
                            + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>No output found</gray>");
                }
            }
            
            // Check for special two-line error case: "Unknown or incomplete command" followed by line with "<--[HERE]"
            if (outputLine.contains("Unknown or incomplete command") && commandIndex + 2 < logs.size()) {
                String nextLine = logs.get(commandIndex + 2);
                if (nextLine != null && nextLine.contains("<--[HERE]")) {
                    // Remove leading ">" if present from next line
                    nextLine = nextLine.trim();
                    if (nextLine.startsWith(">")) {
                        nextLine = nextLine.substring(1).trim();
                    }
                    // Include both lines
                    String combinedOutput = stripLegacyFormatting(outputLine) + "\n" + stripLegacyFormatting(nextLine);
                    return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                            + server.name
                            + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                            + combinedOutput + "</red>");
                }
            }
            
            // Check for "Unknown or incomplete command"
            if (outputLine.contains("Unknown or incomplete command")) {
                return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                        + server
                        + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                        + stripLegacyFormatting(outputLine) + "</red>");
            }
            
            // Check for "<--[HERE]" error indicator
            if (outputLine.contains("<--[HERE]")) {
                return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                        + server.name
                        + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                        + stripLegacyFormatting(outputLine) + "</red>");
            }
            
            // Normal output
            return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + server.name
                    + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>"
                    + stripLegacyFormatting(outputLine) + "</gray>");
        });
    }
    
    /**
     * Strips legacy Minecraft formatting codes (e.g., §a, §r, §l) from a string.
     * These codes are not supported by MiniMessage and will cause parsing errors.
     * 
     * @param text the text to strip formatting codes from
     * @return the text with all legacy formatting codes removed
     */
    private static String stripLegacyFormatting(String text) {
        if (text == null) return null;
        // Remove all § followed by a single character (0-9, a-f, k-o, r)
        // This matches all legacy Minecraft formatting codes
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
}
