package net.slimelabs.vsls.matchmaking.strategies;

import com.protoxon.S4J.entities.Blueprint;
import net.slimelabs.vsls.matchmaking.BlueprintSelectionStrategy;
import net.slimelabs.vsls.matchmaking.registry.GameType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomBlueprintStrategy implements BlueprintSelectionStrategy {

    @Override
    public Blueprint select(GameType gameType) {
        List<Blueprint> blueprints = gameType.getBlueprints();
        if (blueprints == null || blueprints.isEmpty()) return null;
        return blueprints.get(ThreadLocalRandom.current().nextInt(blueprints.size()));
    }
}
