package net.slimelabs.vsls.matchmaking.registry;

import com.protoxon.S4J.entities.Blueprint;
import net.slimelabs.vsls.matchmaking.metadata.BlueprintMetadataParser;
import net.slimelabs.vsls.matchmaking.metadata.MatchmakingMetadata;

import java.util.*;

public class GameTypeRegistry {

    private final Map<String, GameType> gameTypes = new HashMap<>();

    public void load(Collection<Blueprint> blueprints) {

        Map<String, List<Blueprint>> grouped = new HashMap<>();

        for (Blueprint bp : blueprints) {
            MatchmakingMetadata meta = BlueprintMetadataParser.parse(bp);
            if (meta == null) continue;

            grouped.computeIfAbsent(meta.gameType(), k -> new ArrayList<>()).add(bp);
        }

        grouped.forEach((id, list) ->
                gameTypes.put(id, new GameType(id, list))
        );

    }

    public GameType get(String id) {
        return gameTypes.get(id);
    }

}
