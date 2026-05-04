package net.slimelabs.vsls.matchmaking.join;

import com.velocitypowered.api.proxy.Player;
import net.slimelabs.vsls.matchmaking.MatchmakingManager;
import net.slimelabs.vsls.server.Server;

public class JoinService {

    private final MatchmakingManager matchmaking;
    private final DirectServerJoiner direct;

    public JoinService(MatchmakingManager matchmaking, DirectServerJoiner direct) {
        this.matchmaking = matchmaking;
        this.direct = direct;
    }

    public void join(Player player, JoinIntent intent) {
        switch (intent) {
            case GameTypeJoin g -> {
                direct.dequeue(player);
                matchmaking.joinGameType(player, g.gameTypeId());
            }
            case BlueprintJoin b -> {
                direct.dequeue(player);
                matchmaking.joinBlueprint(player, b.blueprintId());
            }
            case SpecificServerJoin s -> {
                matchmaking.dequeue(player);
                direct.join(player, s.serverId());
            }
        }
    }

    /**
     * Connects the player directly to the server with the given id (short or prefix).
     */
    public void joinServer(Player player, String serverId) {
        matchmaking.dequeue(player);
        direct.join(player, serverId);
    }

    /** Queues the player for matchmaking on the given blueprint. */
    public void joinBlueprint(Player player, String blueprintId) {
        direct.dequeue(player);
        matchmaking.joinBlueprint(player, blueprintId);
    }

    /** Queues the player for matchmaking on the given game type. */
    public void joinGameType(Player player, String gameTypeId) {
        direct.dequeue(player);
        matchmaking.joinGameType(player, gameTypeId);
    }

    /** Queues the player to connect when the server is RUNNING, without starting it (e.g. after reset/restart). */
    public void joinWhenReady(Player player, Server server) {
        matchmaking.dequeue(player);
        direct.joinWhenReady(player, server);
    }

    /** Removes the player from matchmaking or direct-server queue. Returns true if they were in any queue. */
    public boolean dequeue(Player player) {
        if (matchmaking.dequeue(player)) return true;
        if (direct.dequeue(player)) return true;
        return false;
    }
}
