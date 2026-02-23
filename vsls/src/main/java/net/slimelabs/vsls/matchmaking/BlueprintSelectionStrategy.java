package net.slimelabs.vsls.matchmaking;

import com.protoxon.S4J.entities.Blueprint;
import net.slimelabs.vsls.matchmaking.registry.GameType;

/**
 * Selects which blueprint to use when provisioning a server for a game type.
 */
public interface BlueprintSelectionStrategy {
    Blueprint select(GameType gameType);
}
