package net.slimelabs.vsls.internal;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.utils.ColorString;

import java.util.Optional;

public class Message {

    public static void Banner() {
        System.out.printf((ColorString.parse(
                "[green]  ___ _    ___\n" +
                        "[green] / __| |  / __|[red] vSLS [yellow]v%s\n" +
                        "[green] \\__ \\ |__\\__ \\[dark_gray] Server Launch System\n" +
                        "[green] |___/____|___/[light_blue] Copyright © 2025 - %d [magenta]%s\n\n" +
                        "[light_blue]Website: [reset]https://slimelabs.net\n" +
                        "[light_blue] Source: [reset]https://github.com/jessefaler/SLS\n" +
                        "[light_blue]License: [reset]https://github.com/jessefaler/SLS/blob/main/LICENSE\n\n" +
                        "[light_blue]This software is made available under the terms of the [magenta]APGL-3.0[light_blue] license.\n" +
                        "[light_blue]The above copyright notice and this permission notice shall be included\n" +
                        "[light_blue]in all copies or substantial portions of the Software.\n"
        )) + "%n", getVersion(), java.time.LocalDate.now().getYear(), getAuthors());
    }

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
