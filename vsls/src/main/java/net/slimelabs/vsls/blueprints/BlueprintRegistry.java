package net.slimelabs.vsls.blueprints;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entites.Blueprint;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BlueprintRegistry {

    private Map<String, Blueprint> blueprints = new HashMap<>();
    private volatile boolean isLoaded = false;
    private final List<Consumer<BlueprintRegistry>> loadCallbacks = new ArrayList<>();

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
        
        // Mark as loaded and execute all pending callbacks
        synchronized (loadCallbacks) {
            isLoaded = true;
            for (Consumer<BlueprintRegistry> callback : loadCallbacks) {
                try {
                    callback.accept(this);
                } catch (Exception e) {
                    Log.error("Error executing blueprint registry load callback", e);
                }
            }
            loadCallbacks.clear();
        }
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
     * Checks if the blueprint registry has been loaded.
     * @return true if the registry has been loaded, false otherwise
     */
    public boolean isLoaded() {
        return isLoaded;
    }

    /**
     * Registers a callback to be executed when the blueprint registry is loaded.
     * If the registry is already loaded, the callback will be executed immediately.
     * If the registry is not yet loaded, the callback will be executed once loading completes.
     *
     * @param callback the callback to execute when the registry is loaded, receives this BlueprintRegistry instance
     */
    public void whenLoaded(Consumer<BlueprintRegistry> callback) {
        synchronized (loadCallbacks) {
            if (isLoaded) {
                // Already loaded, execute immediately
                try {
                    callback.accept(this);
                } catch (Exception e) {
                    Log.error("Error executing blueprint registry load callback", e);
                }
            } else {
                // Not loaded yet, add to callback list
                loadCallbacks.add(callback);
            }
        }
    }

    /**
     * Fetches all blueprints asynchronously from the API and updates the registry.
     * Any errors encountered during the fetch are logged.
     * Note: This will trigger load callbacks again when the reload completes.
     */
    public SLSAction<Void> reload() {
        // Mark as not loaded during reload so callbacks can be registered again
        synchronized (loadCallbacks) {
            isLoaded = false;
        }
        
        return SLS.api.getBlueprints().limit(70)
                .map(loadedBlueprints -> {
                    setBlueprints(loadedBlueprints);
                    Log.info("Reloaded blueprint registry. Loaded {} blueprints", loadedBlueprints.size());
                    return (Void) null;
                })
                .onErrorMap((Throwable failure) -> {
                    Log.warn("Failed to reload blueprints: {}", failure.getMessage());
                    // Mark as loaded and execute callbacks even on error to prevent them from waiting forever
                    synchronized (loadCallbacks) {
                        isLoaded = true;
                        for (Consumer<BlueprintRegistry> callback : loadCallbacks) {
                            try {
                                callback.accept(this);
                            } catch (Exception e) {
                                Log.error("Error executing blueprint registry load callback", e);
                            }
                        }
                        loadCallbacks.clear();
                    }
                    return (Void) null;
                });
    }

    /**
     * Initializes the blueprint registry.
     * <p>
     * Fetches all blueprints asynchronously from the API and populates the registry.
     * If the fetch fails, it will retry every 30 seconds until successful.
     * Any errors encountered during the fetch are logged.
     *
     * @return a new Registry instance with asynchronously loaded blueprints
     */
    public static BlueprintRegistry init() {
        BlueprintRegistry registry = new BlueprintRegistry();
        loadBlueprints(registry);
        return registry;
    }

    /**
     * Attempts to load blueprints from the API. If it fails, schedules a retry after 30 seconds.
     * This will continue retrying until successful.
     *
     * @param registry the registry instance to populate
     */
    private static void loadBlueprints(BlueprintRegistry registry) {
        // Fetches 70 blueprints per page
        SLS.api.getBlueprints().limit(70).executeAsync(blueprints -> {
            registry.setBlueprints(blueprints);
            Log.info("Initialized blueprint registry. Loaded {} blueprints", blueprints.size());
        }, failure -> {
            Log.warn("Failed to load blueprints: {}. Retrying in 30 seconds...", failure.getMessage());
            // Schedule a retry after 30 seconds
            SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> {
                loadBlueprints(registry);
            }).delay(30, TimeUnit.SECONDS).schedule();
        });
    }

}
