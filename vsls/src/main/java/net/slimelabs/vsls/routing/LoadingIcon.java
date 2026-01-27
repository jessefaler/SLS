package net.slimelabs.vsls.routing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LoadingIcon {

    private static final Component[] FRAMES = {
            Component.text("▇▆▅▃▂▂▂▂▂", NamedTextColor.GOLD),
            Component.text("▆▇▆▅▃▂▂▂▂", NamedTextColor.GOLD),
            Component.text("▅▆▇▆▅▃▂▂▂", NamedTextColor.GOLD),
            Component.text("▃▅▆▇▆▅▃▂▂", NamedTextColor.GOLD),
            Component.text("▂▃▅▆▇▆▅▃▂", NamedTextColor.GOLD),
            Component.text("▂▂▃▅▆▇▆▅▃", NamedTextColor.GOLD),
            Component.text("▂▂▂▃▅▆▇▆▅", NamedTextColor.GOLD),
            Component.text("▂▂▂▂▃▅▆▇▆", NamedTextColor.GOLD),
            Component.text("▂▂▂▂▂▃▅▆▇", NamedTextColor.GOLD),
            Component.text("▂▂▂▂▃▅▆▇▆", NamedTextColor.GOLD),
            Component.text("▂▂▂▃▅▆▇▆▅", NamedTextColor.GOLD),
            Component.text("▂▃▅▆▇▆▅▃▂", NamedTextColor.GOLD),
            Component.text("▃▅▆▇▆▅▃▂▂", NamedTextColor.GOLD),
            Component.text("▅▆▇▆▅▃▂▂▂", NamedTextColor.GOLD),
            Component.text("▆▇▆▅▃▂▂▂▂", NamedTextColor.GOLD)
    };

    public Component getFrame(int frame) {
        return FRAMES[frame % FRAMES.length];
    }

}