package net.slimelabs.vsls.log;

import com.velocitypowered.api.proxy.Player;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.utils.message.ProtoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Log {

    private static final Logger logger = LoggerFactory.getLogger("vSLS");
    private static final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();

    /* -----------------  Debug Player Control  ------------------- */

    public static void addDebugPlayer(Player player) {
        debugPlayers.add(player.getUniqueId());
    }

    public static void removeDebugPlayer(Player player) {
        debugPlayers.remove(player.getUniqueId());
    }

    private static void logPlayer(String msg) {
        for (UUID uuid : debugPlayers) {
            SLS.proxy.getPlayer(uuid).ifPresent(player ->
                    ProtoMessage.chat().addMiniMessage(msg).sendMessage(player)
            );
        }
    }

    private static void sendProtoMessageToPlayers(ProtoMessage message) {
        for (UUID uuid : debugPlayers) {
            SLS.proxy.getPlayer(uuid).ifPresent(message::sendMessage);
        }
    }

    /* -----------------  Helper: Format for Players ------------------- */

    private static String formatForPlayer(String level, String msg) {

        String color = switch (level) {
            case "INFO" -> "<color:#0496b0>";
            case "WARN" -> "<color:#c7c742>";
            case "ERROR" -> "<dark_red>";
            case "DEBUG" -> "<gray>";
            default -> "<white>";
        };

        return String.format(
                "<hover:show_text:'<dark_purple>%s</dark_purple>'>" +
                        "<dark_gray>[</dark_gray><color:#0496b0>SLS</color><dark_gray>] " +
                        "[</dark_gray>%s%s<dark_gray>] </dark_gray>" +
                        "<gray>%s</gray></hover>",
                getTimestamp(),
                color, level,
                msg
        );
    }

    /* -----------------  Basic Logging ------------------- */

    public static void info(String msg) {
        logger.info(msg);
        logPlayer(formatForPlayer("INFO", msg));
    }

    public static void warn(String msg) {
        logger.warn(msg);
        logPlayer(formatForPlayer("WARN", msg));
    }

    public static void error(String msg) {
        logger.error(msg);
        logPlayer(formatForPlayer("ERROR", msg));
    }

    public static void debug(String msg) {
        logger.debug(msg);
        logPlayer(formatForPlayer("DEBUG", msg));
    }

    /* -----------------  Formatted Logging (Supports {} args) ------------------- */

    public static void info(String msg, Object... args) {
        logger.info(msg, args);
        String formatted = MessageFormatter.arrayFormat(msg, args).getMessage();
        logPlayer(formatForPlayer("INFO", formatted));
    }

    public static void warn(String msg, Object... args) {
        logger.warn(msg, args);
        String formatted = MessageFormatter.arrayFormat(msg, args).getMessage();
        logPlayer(formatForPlayer("WARN", formatted));
    }

    public static void error(String msg, Object... args) {
        logger.error(msg, args);
        String formatted = MessageFormatter.arrayFormat(msg, args).getMessage();
        logPlayer(formatForPlayer("ERROR", formatted));
    }

    public static void debug(String msg, Object... args) {
        logger.debug(msg, args);
        String formatted = MessageFormatter.arrayFormat(msg, args).getMessage();
        logPlayer(formatForPlayer("DEBUG", formatted));
    }

    /* -----------------  WithField / WithFields ------------------- */

    public static LogEntry withField(String key, Object value) {
        return new LogEntry().withField(key, value);
    }

    public static LogEntry withFields(Map<String, Object> fields) {
        return new LogEntry().withFields(fields);
    }

    /* -----------------  LogEntry class with fields ------------------- */

    public static class LogEntry {

        private final Map<String, Object> fields = new LinkedHashMap<>();

        public LogEntry withField(String key, Object value) {
            fields.put(key, value);
            return this;
        }

        public LogEntry withFields(Map<String, Object> map) {
            fields.putAll(map);
            return this;
        }

        private String formatFields() {
            if (fields.isEmpty()) return "";
            List<String> parts = new ArrayList<>();
            fields.forEach((k, v) -> parts.add(k + "=" + v));
            return "[" + String.join(", ", parts) + "] ";
        }

        public void info(String msg) {
            Log.info(formatFields() + msg);
        }

        public void warn(String msg) {
            Log.warn(formatFields() + msg);
        }

        public void error(String msg) {
            Log.error(formatFields() + msg);
        }

        public void debug(String msg) {
            Log.debug(formatFields() + msg);
        }

        public void info(String msg, Object... args) {
            Log.info(formatFields() + msg, args);
        }

        public void warn(String msg, Object... args) {
            Log.warn(formatFields() + msg, args);
        }

        public void error(String msg, Object... args) {
            Log.error(formatFields() + msg, args);
        }

        public void debug(String msg, Object... args) {
            Log.debug(formatFields() + msg, args);
        }

    }

    /* Logging to a specific target */

    /**
     * Logs to a single target
     * @param target the target to log to
     * @return the target logger
     */
    public static TargetedLogger target(Target target) {
        return new TargetedLogger(target);
    }

    public static class TargetedLogger {
        private final Target target;

        public TargetedLogger(Target target) {
            this.target = target;
        }

        private void send(String level, String msg, Object... args) {
            String formatted = args.length == 0
                    ? msg
                    : MessageFormatter.arrayFormat(msg, args).getMessage();

            switch (target) {
                case CONSOLE -> {
                    switch (level) {
                        case "INFO" -> logger.info(formatted);
                        case "WARN" -> logger.warn(formatted);
                        case "ERROR" -> logger.error(formatted);
                        case "DEBUG" -> logger.debug(formatted);
                    }
                }
                case PLAYER -> {
                    logPlayer(formatForPlayer(level, formatted));
                }
            }
        }

        public void info(String msg, Object... args)  { send("INFO", msg, args); }
        public void warn(String msg, Object... args)  { send("WARN", msg, args); }
        public void error(String msg, Object... args) { send("ERROR", msg, args); }
        public void debug(String msg, Object... args) { send("DEBUG", msg, args); }

        /**
         * Sends a ProtoMessage to the target.
         * If the target is PLAYER, sends the message to all debug players.
         * If the target is CONSOLE, logs the plain text representation of the message.
         *
         * @param message The ProtoMessage to send
         */
        public void sendMessage(ProtoMessage message) {
            switch (target) {
                case PLAYER -> {
                    sendProtoMessageToPlayers(message);
                }
                case CONSOLE -> {
                    // Convert Component to plain text for console logging
                    String plainText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message.asComponent());
                    logger.info(plainText);
                }
            }
        }
    }

    /* -----------------  Timestamp ------------------- */

    public static String getTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd HH:mm:ss.SSS", Locale.ENGLISH);
        return LocalDateTime.now().format(formatter);
    }

    public static Set<UUID> getDebugPlayers() {
        return debugPlayers;
    }

    public enum Target {
        PLAYER,
        CONSOLE;
    }
}