package net.slimelabs.vsls.matchmaking.registry;

import com.protoxon.S4J.entities.Blueprint;

import java.util.List;

public class GameType {

    private final String id;
    private final List<Blueprint> blueprints;

    public GameType(String id, List<Blueprint> blueprints) {
        this.id = id;
        this.blueprints = blueprints;
    }

    public String getId() { return id; }
    public List<Blueprint> getBlueprints() { return blueprints; }

    /** Display name for messages: first blueprint's name if available, otherwise the game type id. */
    public String getDisplayName() {
        if (blueprints == null || blueprints.isEmpty()) return id;
        String name = blueprints.get(0).getName();
        return name != null && !name.isBlank() ? name : id;
    }
}
