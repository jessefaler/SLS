package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.SLSAction;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
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
                    builder.suggest("this");
                    SLS.servers.getShortIds().forEach(builder::suggest);
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
                    Server server;
                    if(id.equals("this")) {
                        if(!(source instanceof Player)) {
                            Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                            return 0;
                        }
                        server = ServerUtils.getServer((Player) source);
                        if(server == null) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Server " + ServerUtils.getServerName((Player) source) + " is not an SLS server", NamedTextColor.RED)
                                    .sendMessage(source);
                            return 0;
                        }
                    } else {
                        server = SLS.servers.resolve(id);
                    }
                    if (server != null) {
                        String finalCommand = command;
                        server.sendCommand(command).executeAsync(success -> {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("Command executed successfully", NamedTextColor.GRAY)
                                    .sendMessage(source);
                            // Try multiple times with increasing delays and log line counts
                            tryCaptureOutput(server, finalCommand, source, 0);
                        }, failure -> Log.requestError("Failed to send command to server " + id, failure, source));
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
     * Attempts to capture console output with increasing delays.
     * <p>
     * Attempts:
     *   100 ms → read 8 lines
     *   800 ms → read 12 lines
     *   3000 ms → read 25 lines
     * <p>
     * Some commands take longer to produce console output due to internal
     * processing or external dependencies, so multiple checks are required.
     * <p>
     * If no output is found after all 3 attempts (total wait ~3.9 seconds),
     * a "No output found" message is sent.
     */
    private static void tryCaptureOutput(Server server, String command, CommandSource source, int attempt) {
        int[] delays = {100, 800, 3000};
        int[] logLines = {8, 12, 25};
        
        if (attempt >= delays.length) {
            // All attempts failed, show "No output found"
            ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + server.getName()
                    + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>No output found</gray>")
                    .sendMessage(source);
            return;
        }
        
        long delay = delays[attempt];
        int lines = logLines[attempt];
        
        SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
            server.getLogs(lines).executeAsync(logs -> {
                String foundOutput = findCommandOutputInLogs(logs, command);
                if (foundOutput != null) {
                    // Found output, format and send it
                    ProtoMessage output = formatCommandOutput(server, foundOutput, logs, findCommandIndex(logs, command));
                    output.sendMessage(source);
                } else {
                    // No output found, try next attempt
                    tryCaptureOutput(server, command, source, attempt + 1);
                }
            }, failure -> {
                // On failure, try next attempt
                tryCaptureOutput(server, command, source, attempt + 1);
            });
        }).delay(delay, TimeUnit.MILLISECONDS).schedule();
    }
    
    /**
     * Finds command output in logs. Returns the output string if found, null otherwise.
     */
    private static String findCommandOutputInLogs(List<String> logs, String command) {
        int commandIndex = findCommandIndex(logs, command);
        if (commandIndex == -1 || commandIndex >= logs.size() - 1) {
            return null;
        }
        
        String outputLine = logs.get(commandIndex + 1);
        if (outputLine == null) {
            return null;
        }
        
        outputLine = outputLine.trim();
        if (outputLine.startsWith(">")) {
            outputLine = outputLine.substring(1).trim();
        }
        
        if (outputLine.isEmpty() || outputLine.equals(">")) {
            if (commandIndex + 2 < logs.size()) {
                String nextLine = logs.get(commandIndex + 2);
                if (nextLine != null) {
                    nextLine = nextLine.trim();
                    if (nextLine.startsWith(">")) {
                        nextLine = nextLine.substring(1).trim();
                    }
                    if (!nextLine.isEmpty() && !nextLine.equals(">")) {
                        return nextLine;
                    }
                }
            }
            return null;
        }
        
        return outputLine;
    }
    
    /**
     * Finds the index of the command in the logs.
     */
    private static int findCommandIndex(List<String> logs, String command) {
        for (int i = logs.size() - 1; i >= 0; i--) {
            String line = logs.get(i);
            if (line == null) continue;
            
            String trimmedLine = line.trim();
            boolean matches = false;
            
            if (trimmedLine.startsWith(">")) {
                String afterPrefix = trimmedLine.substring(1).trim();
                if (afterPrefix.equals(command) || afterPrefix.startsWith(command + " ")) {
                    matches = true;
                }
            } else {
                if (!trimmedLine.startsWith("[")) {
                    if (trimmedLine.equals(command) || trimmedLine.startsWith(command + " ")) {
                        matches = true;
                    }
                }
            }
            
            if (matches) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Formats the command output into a ProtoMessage.
     */
    private static ProtoMessage formatCommandOutput(Server server, String outputLine, List<String> logs, int commandIndex) {
        // Check for special two-line error case: "Unknown or incomplete command" followed by line with "<--[HERE]"
                if (outputLine.contains("Unknown or incomplete command") && commandIndex + 2 < logs.size()) {
            String nextLine = logs.get(commandIndex + 2);
            if (nextLine != null && nextLine.contains("<--[HERE]")) {
                nextLine = nextLine.trim();
                if (nextLine.startsWith(">")) {
                    nextLine = nextLine.substring(1).trim();
                }
                String combinedOutput = stripLegacyFormatting(outputLine) + "\n" + stripLegacyFormatting(nextLine);
                return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                        + server.getName()
                        + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                        + combinedOutput + "</red>");
            }
        }
        
        // Check for "Unknown or incomplete command"
        if (outputLine.contains("Unknown or incomplete command")) {
            return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + server.getName()
                    + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                    + stripLegacyFormatting(outputLine) + "</red>");
        }
        
        // Check for "<--[HERE]" error indicator
        if (outputLine.contains("<--[HERE]")) {
            return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + server.getName()
                    + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                    + stripLegacyFormatting(outputLine) + "</red>");
        }
        
        // Normal output
        return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                + server.getName()
                + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>"
                + stripLegacyFormatting(outputLine) + "</gray>");
    }

    /**
     * Attempts to capture the output of a command that was executed on a server's console.
     * It fetches the specified number of log lines, finds the line containing the command, and returns
     * the line immediately following it as the command's output.
     *
     * @param server  the target server
     * @param command the exact command string that was sent
     * @param logLineCount the number of log lines to fetch
     * @return mapped action containing the detected command output
     */
    public static SLSAction<ProtoMessage> getCommandOutput(Server server, String command, int logLineCount) {
        return server.getLogs(logLineCount).map(logs -> {
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
                    // Make sure it's not a log line (which would start with "[tamp)" times
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
                        + server.getName()
                        + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><gray>No output found</gray>");
            }
            
            // Get the line right after the command
            String outputLine = logs.get(commandIndex + 1);
            if (outputLine == null) {
                return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                        + server.getName()
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
                            + server.getName()
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
                            + server.getName()
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
                        + server.getName()
                        + "</dark_purple>'><dark_gray>[</dark_gray><gold>console</gold><dark_gray>] </dark_gray></hover><red>"
                        + stripLegacyFormatting(outputLine) + "</red>");
            }
            
            // Normal output
            return ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + server.getName()
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
