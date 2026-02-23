package net.slimelabs.vsls.matchmaking.metadata;

import com.protoxon.S4J.entities.Blueprint;

import java.util.Map;
import java.util.Optional;

/**
 * Reads matchmaking metadata from blueprint annotations (vsls.matchmaking).
 * If no matchmaking section is present, falls back to defaults:
 * - gameType = blueprint id
 * - maxPlayers = Integer.MAX_VALUE (effectively unlimited)
 */
public final class BlueprintMetadataParser {

    private BlueprintMetadataParser() {}

    public static MatchmakingMetadata parse(Blueprint blueprint) {
        if (blueprint == null) return null;

        MatchmakingMetadata parsed = null;
        try {
            Object raw = blueprint.getAnnotations();
            if (raw instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Optional<MatchmakingMetadata> opt = MatchmakingParser.parse((Map<String, Object>) map);
                parsed = opt.orElse(null);
            }
        } catch (Exception e) {
            // Annotations missing, wrong shape, or cast failed -> use defaults
            parsed = null;
        }

        int maxPlayers = 10000;

        if(parsed == null) {
            return new MatchmakingMetadata(blueprint.getId(), maxPlayers);
        }

        if(parsed.maxPlayers() > 0) {
                maxPlayers = parsed.maxPlayers();
        }

        if(parsed.gameType() == null) {
            return new MatchmakingMetadata(blueprint.getId(), maxPlayers);
        } else {
            return new MatchmakingMetadata(parsed.gameType(), maxPlayers);
        }

    }
}
