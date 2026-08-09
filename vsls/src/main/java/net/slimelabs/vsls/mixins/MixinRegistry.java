package net.slimelabs.vsls.mixins;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.entities.SLSClient;
import com.protoxon.S4J.entities.Mixin;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class MixinRegistry {

    private ConcurrentHashMap<String, Mixin> mixins = new ConcurrentHashMap<>();
    private final SLSClient api;

    public MixinRegistry(SLSClient api) {
        this.api = api;
        loadMixins(this);
    }

    public void setMixins(Collection<Mixin> mixins) {
        ConcurrentHashMap<String, Mixin> map = new ConcurrentHashMap<>(mixins.size());
        for (Mixin mixin : mixins) {
            map.put(mixin.getId(), mixin);
        }
        this.mixins = map;
    }

    public Collection<String> getIds() {
        return mixins.keySet();
    }

    public Collection<Mixin> getAll() {
        return mixins.values();
    }

    public Mixin getMixin(String id) {
        return find(m -> m.getId().equals(id));
    }

    public Mixin find(Predicate<Mixin> filter) {
        return mixins.values().stream()
                .filter(filter)
                .findFirst()
                .orElse(null);
    }

    public SLSAction<Void> reload() {
        return api.getMixins().limit(100).all()
                .map(loadedMixins -> {
                    setMixins(loadedMixins);
                    Log.info("Reloaded mixin registry. Loaded {} mixins", loadedMixins.size());
                    return (Void) null;
                })
                .onErrorMap((Throwable failure) -> {
                    Log.warn("Failed to reload mixins: {}", failure.getMessage());
                    return (Void) null;
                });
    }

    private static void loadMixins(MixinRegistry registry) {
        registry.api.getMixins().limit(100).all().executeAsync(mixins -> {
            registry.setMixins(mixins);
            Log.info("Initialized mixin registry. Loaded {} mixins", mixins.size());
        }, failure -> {
            Log.warn("Failed to load mixins: {}. Retrying in 30 seconds...", failure.info());
            SLS.proxy.getScheduler().buildTask(SLS.plugin, () -> loadMixins(registry))
                    .delay(30, TimeUnit.SECONDS)
                    .schedule();
        });
    }

}
