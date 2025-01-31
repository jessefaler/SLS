package net.slimelabs.sls.server;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.slimelabs.sls.utils.Message.ProtoMessage;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class Watcher {

    public String serverName;

    public ArrayList<Player> watchers = new ArrayList<>();

    public Watcher(String serverName) {
        this.serverName = serverName;
    }

    public void addWatcher(Player player) {
        watchers.add(player);
    }

    public void removeWatcher(Player player) {
        watchers.remove(player);
    }

    public boolean isWatching(Player player) {
        return watchers.contains(player);
    }

    public void forwardConsoleOutput(String output) {
        output = output.replaceAll("\u001b\\[[;\\d]*m", ""); // Remove color codes
        output = output.replaceAll(">\u001B\\[2K", ""); // Remove control characters

        Pattern timestampPattern = Pattern.compile("\\[(\\d{2}:\\d{2}:\\d{2})\\s(\\w+)]\\s*:");
        Matcher matcher = timestampPattern.matcher(output);

        String timestamp = ""; // Default if no timestamp is found
        String message = output; // Default to the entire output if no timestamp

        // Process message if a timestamp is found
        if (matcher.find()) {
            timestamp = matcher.group(1) + " " + matcher.group(2); // "HH:mm:ss INFO"
            message = output.substring(0, matcher.start()) + output.substring(matcher.end()); // Remove timestamp
        }

        // Remove control characters (non-printing characters, [ESC, CR, etc.])
        message = message.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
        ProtoMessage protoMessage;
        if(timestamp.contains("WARN")) {
            protoMessage = ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + serverName.replace("_", " ") + "</dark_purple><gray> [" + timestamp + "]"
                    + "</gray>'><dark_gray>[</dark_gray><gold>server</gold><dark_gray>] </dark_gray></hover><yellow>"
                    + message + "</yellow>");
        } else if (timestamp.contains("ERROR")) {
            protoMessage = ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + serverName.replace("_", " ") + "</dark_purple><gray> [" + timestamp + "]"
                    + "</gray>'><dark_gray>[</dark_gray><gold>server</gold><dark_gray>] </dark_gray></hover><red>"
                    + message + "</red>");
        } else {
            protoMessage = ProtoMessage.chat().addMiniMessage("<hover:show_text:'<dark_purple>"
                    + serverName.replace("_", " ") + "</dark_purple><gray> [" + timestamp + "]"
                    + "</gray>'><dark_gray>[</dark_gray><gold>server</gold><dark_gray>] </dark_gray></hover><gray>"
                    + message + "</gray>");
        }

        // Send a message to all watchers
        for (Player player : watchers) {
            protoMessage.sendMessage(player);
        }
    }
}
