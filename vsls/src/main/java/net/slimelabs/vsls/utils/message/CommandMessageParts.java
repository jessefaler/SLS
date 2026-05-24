package net.slimelabs.vsls.utils.message;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.slimelabs.vsls.server.Server;

public final class CommandMessageParts {

    private CommandMessageParts() {}

    public static String player(Player player) {
        String currentServer = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("none");
        return "<hover:show_text:'<dark_gray>UUID:</dark_gray> <gray>" + escape(player.getUniqueId().toString()) + "</gray>\n"
                + "<dark_gray>Current server:</dark_gray> <gray>" + escape(currentServer) + "</gray>'>"
                + "<dark_aqua>" + escape(player.getUsername()) + "</dark_aqua></hover>";
    }

    public static String server(Server server) {
        String compositeId = server.getCompositeId();
        String prefix = compositeId;
        String suffix = "";
        int dot = compositeId.lastIndexOf('.');
        if (dot >= 0) {
            prefix = compositeId.substring(0, dot);
            suffix = compositeId.substring(dot);
        }
        return "<hover:show_text:'<dark_gray>Name:</dark_gray> <gray>" + escape(server.getName()) + "</gray>\n"
                + "<dark_gray>Blueprint:</dark_gray> <gray>" + escape(server.getBlueprintId()) + "</gray>\n"
                + "<dark_gray>Status:</dark_gray> <gray>" + escape(server.getStatus().getStatus()) + "</gray>\n"
                + "<dark_gray>Players:</dark_gray> <gray>" + server.getPlayerCount() + "</gray>'>"
                + "<gold>" + escape(prefix) + "</gold><yellow>" + escape(suffix) + "</yellow></hover>";
    }

    public static String text(String value) {
        return escape(value);
    }

    private static String escape(String value) {
        return MiniMessage.miniMessage().escapeTags(value == null ? "" : value);
    }
}
