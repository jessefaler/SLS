package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.entites.Blueprint;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class BlueprintCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("blueprint")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls blueprint", "id"))
                            .sendMessage(source);
                    return 1;
                })
                .then(blueprint());
    }

    private static RequiredArgumentBuilder<CommandSource, String> blueprint() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("blueprint", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.blueprints.getIds().stream().toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "blueprint");

                    Blueprint blueprint = SLS.blueprints.getBlueprint(id);
                    if(blueprint == null) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("No such blueprint " + id, NamedTextColor.RED).sendMessage(source);
                        return 0;
                    }
                    ProtoMessage message = ProtoMessage.chat();
                    String header = getHeader(blueprint);
                    message.addMiniMessage(header);
                    formatBlueprintAsYaml(message, blueprint.getRawJson());
                    message.addMiniMessage("<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>").sendMessage(source);
                    return 0;
                });
    }

    // Returns a header and varies the number of dashes depending on the length of the blueprints name
    private static @NonNull String getHeader(Blueprint blueprint) {
        String name = blueprint.getName();
        int nameLength = name.length();
        int totalWidth = 25;
        int dashCount = (totalWidth - nameLength - 2) / 2;
        dashCount = Math.max(1, dashCount);
        String dashes = "－".repeat(dashCount);
        String msg = "<dark_gray><b><st>\n" +
                dashes + "</st><blue> " + name + " </blue><st>" + dashes +
                "\n</st></b></dark_gray>";
        return msg;
    }

    /**
     * Formats a blueprint JSON object as YAML with colors and proper field ordering.
     * Keys are displayed in dark grey and values in red.
     * Known fields are ordered: metadata, world, server, saving, annotations.
     * Unknown fields are placed at the bottom.
     * Empty lines are added between top-level fields.
     */
    private static ProtoMessage formatBlueprintAsYaml(ProtoMessage message, JSONObject json) {
        // Define known field order
        String[] topLevelOrder = {"metadata", "world", "server", "saving", "annotations"};
        
        // Track which fields we've processed
        Set<String> processedFields = new HashSet<>();
        
        // Process known fields in order
        for (String field : topLevelOrder) {
            if (json.has(field)) {
                if (!processedFields.isEmpty()) {
                    // Add empty line between top-level fields
                    message.add("\n");
                }
                formatField(message, field, json.get(field), 0, null);
                processedFields.add(field);
            }
        }
        
        // Process remaining unknown fields
        for (String key : json.keySet()) {
            if (!processedFields.contains(key)) {
                if (!processedFields.isEmpty()) {
                    // Add empty line between top-level fields
                    message.add("\n");
                }
                formatField(message, key, json.get(key), 0, null);
                processedFields.add(key);
            }
        }
        
        return message;
    }

    /**
     * Formats a single field (key-value pair) with proper indentation and colors.
     */
    private static void formatField(ProtoMessage message, String key, Object value, int indent, String parentKey) {
        String indentStr = "  ".repeat(indent);
        
        // Top-level keys (indent 0) are gold, others are dark grey
        NamedTextColor keyColor = (indent == 0) ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY;
        
        // Add key
        message.add(indentStr + key + ":", keyColor);
        
        if (value == null || value == JSONObject.NULL) {
            message.add(" null\n", NamedTextColor.RED);
        } else if (value instanceof JSONObject) {
            message.add("\n", keyColor);
            formatObject(message, (JSONObject) value, indent + 1, key);
        } else if (value instanceof JSONArray) {
            message.add("\n", keyColor);
            formatArray(message, (JSONArray) value, indent + 1, key);
        } else if (value instanceof String) {
            String str = (String) value;
            // Quote strings that need quoting
            if (needsQuoting(str)) {
                message.add(" \"" + escapeString(str) + "\"\n", NamedTextColor.RED);
            } else {
                message.add(" " + str + "\n", NamedTextColor.RED);
            }
        } else if (value instanceof Number || value instanceof Boolean) {
            message.add(" " + value.toString() + "\n", NamedTextColor.RED);
        } else {
            message.add(" " + value.toString() + "\n", NamedTextColor.RED);
        }
    }

    /**
     * Formats a JSON object with proper field ordering for known nested structures.
     */
    private static void formatObject(ProtoMessage message, JSONObject obj, int indent, String parentKey) {
        // Define known field orders for nested objects
        Map<String, String[]> knownOrders = new HashMap<>();
        knownOrders.put("metadata", new String[]{"id", "name", "type"});
        knownOrders.put("world", new String[]{"name", "authors", "path"});
        knownOrders.put("server", new String[]{"software", "version", "image", "path", "limits", "configs", "content"});
        knownOrders.put("saving", new String[]{"enabled"});
        knownOrders.put("limits", new String[]{"memory_limit", "swap", "io_weight", "cpu_limit", "disk_space", "threads", "oom_disabled"});
        
        // Check if this is a config object (has parser and find fields)
        boolean isConfigObject = obj.has("parser") && obj.has("find");
        if (isConfigObject) {
            knownOrders.put("__config__", new String[]{"parser", "find"});
            parentKey = "__config__";
        }
        
        // Get field order based on parent key
        String[] fieldOrder = knownOrders.get(parentKey);
        
        Set<String> processedFields = new HashSet<>();
        
        // Process known fields in order if we have an order
        if (fieldOrder != null) {
            for (String field : fieldOrder) {
                if (obj.has(field)) {
                    formatField(message, field, obj.get(field), indent, parentKey);
                    processedFields.add(field);
                }
            }
        }
        
        // Process remaining fields (for objects like configs, content, annotations where order doesn't matter)
        for (String key : obj.keySet()) {
            if (!processedFields.contains(key)) {
                formatField(message, key, obj.get(key), indent, parentKey);
                processedFields.add(key);
            }
        }
    }

    /**
     * Formats a JSON array.
     */
    private static void formatArray(ProtoMessage message, JSONArray array, int indent, String parentKey) {
        String indentStr = "  ".repeat(indent);
        
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            
            if (item == null || item == JSONObject.NULL) {
                message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                message.add(" null\n", NamedTextColor.RED);
            } else if (item instanceof JSONObject) {
                // For array items that are objects, format the first field inline with the dash
                JSONObject obj = (JSONObject) item;
                String[] keys = obj.keySet().toArray(new String[0]);
                
                if (keys.length > 0) {
                    // Add dash and first key-value on same line
                    String firstKey = keys[0];
                    Object firstValue = obj.get(firstKey);
                    message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                    
                    // Format first field inline
                    formatArrayItemField(message, firstKey, firstValue, indent, false);
                    
                    // Format remaining fields with proper indentation
                    for (int j = 1; j < keys.length; j++) {
                        formatArrayItemField(message, keys[j], obj.get(keys[j]), indent, true);
                    }
                } else {
                    // Empty object
                    message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                    message.add(" {}\n", NamedTextColor.RED);
                }
            } else if (item instanceof JSONArray) {
                message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                message.add("\n", NamedTextColor.DARK_GRAY);
                formatArray(message, (JSONArray) item, indent + 1, parentKey);
            } else if (item instanceof String) {
                String str = (String) item;
                message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                if (needsQuoting(str)) {
                    message.add(" \"" + escapeString(str) + "\"\n", NamedTextColor.RED);
                } else {
                    message.add(" " + str + "\n", NamedTextColor.RED);
                }
            } else {
                message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                message.add(" " + item.toString() + "\n", NamedTextColor.RED);
            }
        }
    }

    /**
     * Formats a field within an array item object.
     * @param isSubsequentField true if this is not the first field (needs newline and indentation)
     */
    private static void formatArrayItemField(ProtoMessage message, String key, Object value, int arrayIndent, boolean isSubsequentField) {
        String indentStr = "  ".repeat(arrayIndent);
        
        if (isSubsequentField) {
            // Subsequent fields: newline, indent to align with first field's key
            // The first field is: indentStr + "- " + key, so subsequent fields need indentStr + "  " to align
            // (two spaces to account for the "- " before the first key)
            message.add(indentStr + "  ", NamedTextColor.DARK_GRAY);
        } else {
            // First field: space after dash
            message.add(" ", NamedTextColor.DARK_GRAY);
        }
        
        // Add key
        message.add(key + ":", NamedTextColor.DARK_GRAY);
        
        if (value == null || value == JSONObject.NULL) {
            message.add(" null\n", NamedTextColor.RED);
        } else if (value instanceof JSONObject) {
            message.add("\n", NamedTextColor.DARK_GRAY);
            // For nested objects in array items, indent to align with the key's value position
            // The key is at arrayIndent + 1 (after the dash and space), so the object should be at arrayIndent + 1
            formatObject(message, (JSONObject) value, arrayIndent + 1, null);
        } else if (value instanceof JSONArray) {
            message.add("\n", NamedTextColor.DARK_GRAY);
            formatArray(message, (JSONArray) value, arrayIndent + 1, null);
        } else if (value instanceof String) {
            String str = (String) value;
            if (needsQuoting(str)) {
                message.add(" \"" + escapeString(str) + "\"\n", NamedTextColor.RED);
            } else {
                message.add(" " + str + "\n", NamedTextColor.RED);
            }
        } else if (value instanceof Number || value instanceof Boolean) {
            message.add(" " + value.toString() + "\n", NamedTextColor.RED);
        } else {
            message.add(" " + value.toString() + "\n", NamedTextColor.RED);
        }
    }

    /**
     * Checks if a string needs to be quoted in YAML.
     */
    private static boolean needsQuoting(String str) {
        if (str.isEmpty()) return true;
        // Quote if it contains special characters, starts with number, or is a boolean/null keyword
        return str.matches(".*[:#@`|>\\[\\]{}%&*!?\\\\].*") ||
               str.matches("^[0-9].*") ||
               str.equalsIgnoreCase("true") ||
               str.equalsIgnoreCase("false") ||
               str.equalsIgnoreCase("null") ||
               str.equalsIgnoreCase("yes") ||
               str.equalsIgnoreCase("no") ||
               str.contains("\n") ||
               str.trim().length() != str.length();
    }

    /**
     * Escapes special characters in a string for YAML.
     */
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

}
