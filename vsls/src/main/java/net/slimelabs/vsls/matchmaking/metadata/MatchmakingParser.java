package net.slimelabs.vsls.matchmaking.metadata;

import java.util.Map;
import java.util.Optional;

/**
 * Parses the matchmaking metadata from the blueprint annotations
 */
public class MatchmakingParser {

    public static Optional<MatchmakingMetadata> parse(Map<String, Object> annotations) {

        if (annotations == null) return Optional.empty();

        Object vslsObj = annotations.get("vsls");
        if (!(vslsObj instanceof Map<?, ?> vslsMap)) {
            return Optional.empty();
        }

        Object mmObj = vslsMap.get("matchmaking");
        if (!(mmObj instanceof Map<?, ?> mmMap)) {
            return Optional.empty();
        }

        try {
            String gameType = (String) mmMap.get("gameType");
            Object maxPlayersObj = mmMap.get("maxPlayers");
            int maxPlayers = toInt(maxPlayersObj);

            // Require at least maxPlayers; gameType is optional (caller uses blueprint id when null/blank)
            if (maxPlayers <= 0) return Optional.empty();

            String effectiveGameType = (gameType != null && !gameType.isBlank()) ? gameType : null;
            return Optional.of(new MatchmakingMetadata(
                    effectiveGameType,
                    maxPlayers
            ));

        } catch (ClassCastException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static int toInt(Object value) {
        if (value == null) return 0; // missing => treat as invalid, caller will use default
        if (value instanceof Number n) return n.intValue();
        return 0;
    }
}
