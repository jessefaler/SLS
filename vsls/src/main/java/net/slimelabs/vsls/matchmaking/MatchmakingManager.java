package net.slimelabs.vsls.matchmaking;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.blueprints.BlueprintRegistry;
import net.slimelabs.vsls.matchmaking.metadata.BlueprintMetadataParser;
import net.slimelabs.vsls.matchmaking.metadata.MatchmakingMetadata;
import net.slimelabs.vsls.matchmaking.registry.GameType;
import net.slimelabs.vsls.matchmaking.registry.GameTypeRegistry;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MatchmakingManager {

    private final Map<String, MatchmakingPool> pools = new ConcurrentHashMap<>();
    private final GameTypeRegistry registry;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintSelectionStrategy strategy;

    public MatchmakingManager(
            GameTypeRegistry registry,
            BlueprintRegistry blueprintRegistry,
            BlueprintSelectionStrategy strategy) {
        this.registry = registry;
        this.blueprintRegistry = blueprintRegistry;
        this.strategy = strategy;
        SLS.proxy.getEventManager().register(SLS.plugin, this);
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Server server = event.getPlayer().getCurrentServer()
                .map(conn -> SLS.servers.resolve(conn.getServerInfo().getName()))
                .orElse(null);
        if (server == null) return;
        for (MatchmakingPool pool : pools.values()) {
            pool.onPlayerConnectedToServer(event.getPlayer(), server);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        for (MatchmakingPool pool : pools.values()) {
            pool.onPlayerDisconnected(event.getPlayer());
        }
    }

    public void joinGameType(Player player, String gameTypeId) {
        GameType gameType = registry.get(gameTypeId);
        if (gameType == null) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .add("Unknown game type: " + gameTypeId, NamedTextColor.RED)
                    .sendMessage(player);
            return;
        }
        MatchmakingPool existing = getPoolForPlayer(player);
        if (existing != null && existing.getGameType().getId().equals(gameTypeId)) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<gradient:#9d70ff:#00ffff>You are already in queue for " + existing.getGameType().getDisplayName() + "</gradient>")
                    .sendMessage(player);
            return;
        }
        if (existing != null) {
            existing.dequeue(player);
        }
        pools.computeIfAbsent(gameTypeId,
                id -> new MatchmakingPool(registry.get(id), blueprintRegistry, strategy)
        ).enqueue(player, null);
    }

    public void joinBlueprint(Player player, String blueprintId) {
        var bp = blueprintRegistry.getBlueprint(blueprintId);
        if (bp == null) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .add("Unknown blueprint: " + blueprintId, NamedTextColor.RED)
                    .sendMessage(player);
            return;
        }
        MatchmakingMetadata meta = BlueprintMetadataParser.parse(bp);
        if (meta == null) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .add("Blueprint " + bp.getName() + " is not configured for matchmaking.", NamedTextColor.RED)
                    .sendMessage(player);
            return;
        }
        String gameTypeId = meta.gameType();
        GameType gameType = registry.get(gameTypeId);
        if (gameType == null) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .add("No game type found for: " + gameTypeId, NamedTextColor.RED)
                    .sendMessage(player);
            return;
        }
        MatchmakingPool existingBp = getPoolForPlayer(player);
        if (existingBp != null && existingBp.getGameType().getId().equals(gameTypeId)) {
            ProtoMessage.chat()
                    .add(MessagePreset.SLS)
                    .addMiniMessage("<gradient:#9d70ff:#00ffff>You are already in queue for " + existingBp.getGameType().getDisplayName() + "</gradient>")
                    .sendMessage(player);
            return;
        }
        if (existingBp != null) {
            existingBp.dequeue(player);
        }
        pools.computeIfAbsent(gameTypeId,
                id -> new MatchmakingPool(registry.get(id), blueprintRegistry, strategy)
        ).enqueue(player, blueprintId);
    }

    /** Finds the pool that has this player in the waiting queue, or null. */
    public MatchmakingPool getPoolForPlayer(Player player) {
        for (MatchmakingPool pool : pools.values()) {
            if (pool.isQueued(player)) return pool;
        }
        return null;
    }

    /** Removes the player from whichever pool they are in. Returns true if they were queued. */
    public boolean dequeue(Player player) {
        MatchmakingPool pool = getPoolForPlayer(player);
        return pool != null && pool.dequeue(player);
    }
}
