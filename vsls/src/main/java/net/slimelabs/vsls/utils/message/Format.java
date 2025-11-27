package net.slimelabs.vsls.utils.message;

import net.kyori.adventure.text.format.TextDecoration;

public enum Format {
    BOLD(TextDecoration.BOLD),
    ITALIC(TextDecoration.ITALIC),
    UNDERLINED(TextDecoration.UNDERLINED),
    STRIKETHROUGH(TextDecoration.STRIKETHROUGH),
    OBFUSCATED(TextDecoration.OBFUSCATED);

    private final TextDecoration decoration;

    Format(TextDecoration decoration) {
        this.decoration = decoration;
    }

    public TextDecoration getDecoration() {
        return decoration;
    }
}
