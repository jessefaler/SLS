package net.slimelabs.vsls.utils;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import net.slimelabs.vsls.SLS;

import java.util.Optional;

public class PluginInfo {

    public static String getVersion() {
        Optional<PluginContainer> pluginContainer = SLS.proxy.getPluginManager().getPlugin("vsls");
        if (pluginContainer.isPresent()) {
            PluginDescription description = pluginContainer.get().getDescription();
            return description.getVersion().orElse("unknown");
        }
        return "unknown";
    }

    public static String getAuthors() {
        return SLS.proxy.getPluginManager()
                .getPlugin("vsls")
                .map(plugin -> String.join(", ", plugin.getDescription().getAuthors()))
                .orElse("unknown");
    }

}
