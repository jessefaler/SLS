package net.slimelabs.vsls.blueprints;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entites.Blueprint;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BlueprintRegistry {

    private Map<String, Blueprint> blueprints = new HashMap<>();

    /**
     * Adds a blueprint to the registry
     * @param blueprint the blueprint to add
     */
    public void add(Blueprint blueprint) {
        blueprints.put(blueprint.getId(), blueprint);
    }

    /**
     * Replaces all blueprints in the registry with the provided collection.
     * @param blueprints the collection of blueprints to set
     */
    public void setBlueprints(Collection<Blueprint> blueprints) {
        Map<String, Blueprint> map = new HashMap<>(blueprints.size());
        for (Blueprint blueprint : blueprints) {
            map.put(blueprint.getId(), blueprint);
        }
        this.blueprints = map;
    }

    /**
     * Gets the id's of all blueprints in the registry
     * @return Collection of all blueprint id's
     */
    public Collection<String> getIds() {
        return blueprints.keySet();
    }

    /**
     * Gets the id's of all blueprints with a certain type
     * @return Collection of all blueprint id's
     */
    public Collection<String> getIds(String type) {
        return blueprints.values().stream()
                .filter(b -> b.getType().equals(type))
                .map(Blueprint::getId)
                .collect(Collectors.toList());
    }

    /**
     * Gets all blueprints in the registry
     * @return collection of all the blueprints
     */
    public Collection<Blueprint> getAll() {
        return blueprints.values();
    }

    /**
     * Gets the unique types of all blueprints in the registry
     * @return Collection of all blueprint types
     */
    public Collection<String> getTypes() {
        return blueprints.values().stream()
                .map(Blueprint::getType)
                .collect(Collectors.toSet());
    }

    /**
     * Gets the blueprint with the matching id or null if none are found
     * @param id the id to look for
     * @return the blueprint matching the id or null if none are found
     */
    public Blueprint getBlueprint(String id) {
        return find(b -> b.getId().equals(id));
    }

    /**
     * Find returns a single element from the collection matching the filter. If
     * nothing is found, a nil result is returned.
     * @param filter the filter to use in the search
     * @return the blueprint that matched the filter or null if none were found
     */
    public Blueprint find(Predicate<Blueprint> filter) {
        return blueprints.values().stream()
                .filter(filter)
                .findFirst()
                .orElse(null);
    }

    /**
     * Fetches all blueprints asynchronously from the API and updates the registry.
     * Any errors encountered during the fetch are logged.
     */
    public SLSAction<Void> reload() {
        return SLS.api.getBlueprints().limit(70)
                .map(loadedBlueprints -> {
                    setBlueprints(loadedBlueprints);
                    Log.info("Reloaded blueprint registry. Loaded {} blueprints", loadedBlueprints.size());
                    return (Void) null;
                })
                .onErrorMap((Throwable failure) -> {
                    SLS.logger.warn("Failed to reload blueprints: {}", failure.getMessage());
                    return (Void) null;
                });
    }

    /**
     * Initializes the blueprint registry.
     * <p>
     * Fetches all blueprints asynchronously from the API and populates the registry.
     * Any errors encountered during the fetch are logged.
     *
     * @return a new Registry instance with asynchronously loaded blueprints
     */
    public static BlueprintRegistry init() {
        BlueprintRegistry registry = new BlueprintRegistry();
        // Fetches 70 blueprints per page
        SLS.api.getBlueprints().limit(70).executeAsync(blueprints -> {
            registry.setBlueprints(blueprints);
            SLS.logger.info("Initialized blueprint registry. Loaded {} blueprints", blueprints.size());
        }, failure -> {
            SLS.logger.warn("Failed to load blueprints: {}", failure.getMessage());
        });
        return registry;
    }

}
