package net.slimelabs.sls.server;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.slimelabs.sls.utils.Message.ProtoMessage;

import java.util.ArrayList;
import java.util.Map;

public class Watcher {

    public ArrayList<Player> watchers = new ArrayList<>();

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
        ProtoMessage protoMessage = ProtoMessage.chat()
                .add("[", NamedTextColor.DARK_GRAY)
                .add("Server", NamedTextColor.GOLD)
                .add("] ", NamedTextColor.DARK_GRAY)
                .add(output, NamedTextColor.GRAY);
        for(Player player : watchers) {
            protoMessage.sendMessage(player);
        }
    }
}
