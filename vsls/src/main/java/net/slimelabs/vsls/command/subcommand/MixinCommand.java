package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.entities.Mixin;
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

public class MixinCommand {

    /** Top-level order matches protocube/blueprint/mixin.go Mixin struct. */
    private static final String[] TOP_LEVEL_ORDER = {
            "mixin", "extends", "server", "state", "annotations"
    };

    private static final Map<String, String[]> FIELD_ORDERS = Map.of(
            "mixin", new String[]{"id", "description"},
            "server", new String[]{"software", "version", "image", "path", "limits", "configs"},
            "limits", new String[]{"memory_limit", "swap", "io_weight", "cpu_limit", "disk_space", "threads", "oom_disabled"},
            "state", new String[]{"volumes", "mounts", "copy", "env"},
            "volumes", new String[]{"name", "source", "target", "mode"},
            "mounts", new String[]{"source", "target", "read_only"},
            "copy", new String[]{"source", "target"},
            "__config__", new String[]{"parser", "find"}
    );

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("mixin")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls mixin", "id"))
                            .sendMessage(source);
                    return 1;
                })
                .then(mixin());
    }

    private static RequiredArgumentBuilder<CommandSource, String> mixin() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("mixin", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.mixins.getIds().stream().toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "mixin");

                    Mixin mixin = SLS.mixins.getMixin(id);
                    if (mixin == null) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("No such mixin " + id, NamedTextColor.RED).sendMessage(source);
                        return 0;
                    }
                    ProtoMessage message = ProtoMessage.chat();
                    message.addMiniMessage(getHeader(mixin));
                    formatMixinAsYaml(message, mixin.getRawJson());
                    message.addMiniMessage("<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>").sendMessage(source);
                    return 0;
                });
    }

    private static @NonNull String getHeader(Mixin mixin) {
        String name = mixin.getId();
        int nameLength = name.length();
        int totalWidth = 25;
        int dashCount = Math.max(1, (totalWidth - nameLength - 2) / 2);
        String dashes = "－".repeat(dashCount);
        return "<dark_gray><b><st>\n" +
                dashes + "</st><blue> " + name + " </blue><st>" + dashes +
                "\n</st></b></dark_gray>";
    }

    private static ProtoMessage formatMixinAsYaml(ProtoMessage message, JSONObject json) {
        boolean wroteField = false;

        for (String field : TOP_LEVEL_ORDER) {
            if (!json.has(field) || isEmptyDisplayValue(json.get(field))) {
                continue;
            }
            if (wroteField) {
                message.add("\n");
            }
            formatField(message, field, json.get(field), 0);
            wroteField = true;
        }

        for (String key : json.keySet()) {
            if (isKnownTopLevel(key) || isEmptyDisplayValue(json.get(key))) {
                continue;
            }
            if (wroteField) {
                message.add("\n");
            }
            formatField(message, key, json.get(key), 0);
            wroteField = true;
        }

        return message;
    }

    private static boolean isKnownTopLevel(String key) {
        for (String field : TOP_LEVEL_ORDER) {
            if (field.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Skip null, empty strings, empty arrays, and objects whose fields are all empty. */
    private static boolean isEmptyDisplayValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return true;
        }
        if (value instanceof String str) {
            return str.isEmpty();
        }
        if (value instanceof JSONArray arr) {
            if (arr.isEmpty()) {
                return true;
            }
            for (int i = 0; i < arr.length(); i++) {
                if (!isEmptyDisplayValue(arr.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (value instanceof JSONObject obj) {
            if (obj.isEmpty()) {
                return true;
            }
            for (String key : obj.keySet()) {
                if (!isEmptyDisplayValue(obj.get(key))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static void formatField(ProtoMessage message, String key, Object value, int indent) {
        if (isEmptyDisplayValue(value)) {
            return;
        }

        String indentStr = "  ".repeat(indent);
        NamedTextColor keyColor = (indent == 0) ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY;

        message.add(indentStr + key + ":", keyColor);

        if (value instanceof JSONObject) {
            message.add("\n", keyColor);
            formatObject(message, (JSONObject) value, indent + 1, key);
        } else if (value instanceof JSONArray) {
            message.add("\n", keyColor);
            formatArray(message, (JSONArray) value, indent + 1, key);
        } else if (value instanceof String str) {
            if (needsQuoting(str)) {
                message.add(" \"" + escapeString(str) + "\"\n", NamedTextColor.RED);
            } else {
                message.add(" " + str + "\n", NamedTextColor.RED);
            }
        } else if (value instanceof Number || value instanceof Boolean) {
            message.add(" " + value + "\n", NamedTextColor.RED);
        } else {
            message.add(" " + value + "\n", NamedTextColor.RED);
        }
    }

    private static void formatObject(ProtoMessage message, JSONObject obj, int indent, String parentKey) {
        String orderKey = parentKey;
        if (obj.has("parser") && obj.has("find")) {
            orderKey = "__config__";
        }

        String[] fieldOrder = FIELD_ORDERS.get(orderKey);
        Set<String> processedFields = new HashSet<>();

        if (fieldOrder != null) {
            for (String field : fieldOrder) {
                if (obj.has(field) && !isEmptyDisplayValue(obj.get(field))) {
                    formatField(message, field, obj.get(field), indent);
                    processedFields.add(field);
                }
            }
        }

        for (String key : obj.keySet()) {
            if (!processedFields.contains(key) && !isEmptyDisplayValue(obj.get(key))) {
                formatField(message, key, obj.get(key), indent);
                processedFields.add(key);
            }
        }
    }

    private static void formatArray(ProtoMessage message, JSONArray array, int indent, String parentKey) {
        String indentStr = "  ".repeat(indent);

        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (isEmptyDisplayValue(item)) {
                continue;
            }

            if (item instanceof JSONObject obj) {
                List<String> keys = orderedKeys(obj, parentKey);

                if (!keys.isEmpty()) {
                    String firstKey = keys.get(0);
                    message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                    formatArrayItemField(message, firstKey, obj.get(firstKey), indent, false);
                    for (int j = 1; j < keys.size(); j++) {
                        formatArrayItemField(message, keys.get(j), obj.get(keys.get(j)), indent, true);
                    }
                }
            } else if (item instanceof JSONArray nested) {
                message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                message.add("\n", NamedTextColor.DARK_GRAY);
                formatArray(message, nested, indent + 1, parentKey);
            } else if (item instanceof String str) {
                message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                if (needsQuoting(str)) {
                    message.add(" \"" + escapeString(str) + "\"\n", NamedTextColor.RED);
                } else {
                    message.add(" " + str + "\n", NamedTextColor.RED);
                }
            } else {
                message.add(indentStr + "-", NamedTextColor.DARK_GRAY);
                message.add(" " + item + "\n", NamedTextColor.RED);
            }
        }
    }

    private static List<String> orderedKeys(JSONObject obj, String parentKey) {
        List<String> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] order = FIELD_ORDERS.get(parentKey);
        if (order != null) {
            for (String key : order) {
                if (obj.has(key) && !isEmptyDisplayValue(obj.get(key))) {
                    keys.add(key);
                    seen.add(key);
                }
            }
        }
        for (String key : obj.keySet()) {
            if (!seen.contains(key) && !isEmptyDisplayValue(obj.get(key))) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static void formatArrayItemField(ProtoMessage message, String key, Object value, int arrayIndent, boolean isSubsequentField) {
        if (isEmptyDisplayValue(value)) {
            return;
        }

        String indentStr = "  ".repeat(arrayIndent);

        if (isSubsequentField) {
            message.add(indentStr + "  ", NamedTextColor.DARK_GRAY);
        } else {
            message.add(" ", NamedTextColor.DARK_GRAY);
        }

        message.add(key + ":", NamedTextColor.DARK_GRAY);

        if (value instanceof JSONObject obj) {
            message.add("\n", NamedTextColor.DARK_GRAY);
            formatObject(message, obj, arrayIndent + 1, key);
        } else if (value instanceof JSONArray arr) {
            message.add("\n", NamedTextColor.DARK_GRAY);
            formatArray(message, arr, arrayIndent + 1, key);
        } else if (value instanceof String str) {
            if (needsQuoting(str)) {
                message.add(" \"" + escapeString(str) + "\"\n", NamedTextColor.RED);
            } else {
                message.add(" " + str + "\n", NamedTextColor.RED);
            }
        } else if (value instanceof Number || value instanceof Boolean) {
            message.add(" " + value + "\n", NamedTextColor.RED);
        } else {
            message.add(" " + value + "\n", NamedTextColor.RED);
        }
    }

    private static boolean needsQuoting(String str) {
        if (str.isEmpty()) return true;
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

    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

}
