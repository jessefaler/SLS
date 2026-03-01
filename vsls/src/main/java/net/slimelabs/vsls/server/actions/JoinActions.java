package net.slimelabs.vsls.server.actions;

import com.protoxon.S4J.entities.Blueprint;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JoinActions {

    private final Server server;
    private final List<String> commands;

    public JoinActions(Server server) {
        this.server = server;
        // Parse the on join commands from the blueprints annotations
        // These commands will run everytime a player joins the server
        this.commands = parseCommandAnnotations();
        // Register the post connection event listener
        SLS.proxy.getEventManager().register(SLS.plugin, this);
    }

    @Subscribe
    public void onServerPostConnect(ServerConnectedEvent event) {
        if(event.getServer().getServerInfo().getName().equals(server.getShortId())) {
            runJoinCommands(event.getPlayer());
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> parseCommandAnnotations() {
        Blueprint blueprint = SLS.blueprints.getBlueprint(server.getBlueprintId());
        if (blueprint == null) {
            return Collections.emptyList();
        }

        Object annotationsRaw = blueprint.getAnnotations();
        if (!(annotationsRaw instanceof Map<?, ?> root)) {
            return Collections.emptyList();
        }

        Object vslsObj = root.get("vsls");
        if (!(vslsObj instanceof Map<?, ?> vslsMap)) {
            return Collections.emptyList();
        }

        Object onJoinObj = vslsMap.get("on-join");
        if (!(onJoinObj instanceof Collection<?> entries)) {
            return Collections.emptyList();
        }

        return entries.stream()
                .filter(e -> e instanceof Map<?, ?>)
                .map(e -> (Map<String, Object>) e)
                .map(m -> m.get("run"))
                .filter(v -> v instanceof String)
                .map(v -> (String) v)
                .toList();
    }

    public void runJoinCommands(Player player) {
        if(!commands.isEmpty()) {
            server.sendCommands(
                    commands.stream()
                            .map(cmd -> cmd.replace("{PLAYER_NAME}", player.getUsername()))
                            .toList()
            ).executeAsync(success -> {}, failure -> {
                Log.warn("JoinActions: failed to run commands on player join. reason: " + failure.getMessage());
            });
        }
    }

}
