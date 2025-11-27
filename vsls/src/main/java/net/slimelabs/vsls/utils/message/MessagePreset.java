package net.slimelabs.vsls.utils.message;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

// Enum to define common presets
public enum MessagePreset {

    SLS {
        @Override
        public ProtoMessage applyPreset(ProtoMessage message) {
            message.add("[", NamedTextColor.GRAY)
                    .add("SLS", TextColor.color(99, 255, 122))
                    .add("] ", NamedTextColor.GRAY);
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
