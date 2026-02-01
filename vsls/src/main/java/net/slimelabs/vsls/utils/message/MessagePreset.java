package net.slimelabs.vsls.utils.message;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.vsls.utils.PluginInfo;

// Enum to define common presets
public enum MessagePreset {

    SLS {
        @Override
        public ProtoMessage applyPreset(ProtoMessage message) {
            message.addMiniMessage("<hover:show_text:'<red>Server Launch System</red>\n<color:#3d98ff>By " + PluginInfo.getAuthors() + "</color>'><dark_gray>[<gradient:#2e70ff:#4797ff>SLS</gradient>] </dark_gray></hover>");
            return message;
        }
    },
    INCORRECT_COMMAND_USAGE {
        @Override
        public ProtoMessage applyPreset(ProtoMessage message) {
            message.add(MessagePreset.SLS)
                    .add("Incorrect Command Usage! ", TextColor.color(237, 67, 55));
            return message;
        }
    };

    // Abstract method to apply the preset
    public abstract ProtoMessage applyPreset(ProtoMessage message);
}
