package net.slimelabs.sls.utils.Message;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.ComponentLike;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

public class Message implements ComponentLike {
    private final TextComponent.Builder messageBuilder;
    private final MessageType messageType;

    // Private constructor to prevent direct instantiation
    private Message(MessageType messageType) {
        this.messageBuilder = Component.text();
        this.messageType = messageType;
    }

    // Static method to create a chat message
    public static Message chat() {
        return new Message(MessageType.CHAT);
    }

    // Static method to create an action bar message
    public static Message actionBar() {
        return new Message(MessageType.ACTION_BAR);
    }

    /**
     * Adds a word or phrase to the message with a specific RGB color.
     * It is recommended to use {@link TextColor} instead of directly passing RGB values to take advantage of the color wheel in IDEs.
     *
     * @param text  The word or phrase to add.
     * @param red   Red component (0-255) of the RGB color.
     * @param green Green component (0-255) of the RGB color.
     * @param blue  Blue component (0-255) of the RGB color.
     * @return The current {@link Message} instance.
     */
    public Message add(String text, int red, int green, int blue) {
        this.messageBuilder.append(
                Component.text(text).color(TextColor.color(red, green, blue))
        );
        return this;
    }

    /**
     * Adds a MiniMessage-formatted string to the message.
     *
     * @param miniMessage The MiniMessage-formatted string.
     * @return The current {@link Message} instance.
     */
    public Message addMiniMessage(String miniMessage) {
        MiniMessage miniMessageParser = MiniMessage.miniMessage();
        Component parsedComponent = miniMessageParser.deserialize(miniMessage);
        this.messageBuilder.append(parsedComponent);
        return this;
    }

    /**
     * Adds a word or phrase to the message with a specific {@link TextColor}.
     *
     * @param text  The word or phrase to add.
     * @param color The {@link TextColor} to apply to the text.
     * @return The current {@link Message} instance.
     */
    public Message add(String text, TextColor color) {
        this.messageBuilder.append(Component.text(text).color(color));
        return this;
    }

    /**
     * Adds a word or phrase to the message with a named color.
     *
     * @param text  The word or phrase to add.
     * @param color The NamedTextColor to apply.
     * @return The current Message instance.
     */
    public Message add(String text, NamedTextColor color) {
        this.messageBuilder.append(
                Component.text(text).color(color)
        );
        return this;
    }

    /**
     * Adds a word or phrase to the message without any color.
     *
     * @param text The word or phrase to add.
     * @return The current Message instance.
     */
    public Message add(String text) {
        this.messageBuilder.append(Component.text(text));
        return this;
    }

    /**
     * Converts this Message into a Component.
     *
     * @return A Component representing the constructed message.
     */
    @Override
    public @NotNull Component asComponent() {
        return this.messageBuilder.build();
    }

    /**
     * Apply a preset message format.
     *
     * @param preset The preset to apply (e.g., SLS).
     * @return The current {@link Message} instance.
     */
    public Message add(MessagePreset preset) {
        return preset.applyPreset(this);
    }

    /**
     * Apply a preset message format.
     *
     * @param formatter The preset to apply (e.g., SLS).
     * @return The current {@link Message} instance.
     */
    public Message add(MessageFormatter formatter) {
        return formatter.usage(this);
    }

    /**
     * Adds a word or phrase to the message with a specific {@link TextColor} and {@link Format}.
     *
     * @param text   The word or phrase to add.
     * @param color  The {@link TextColor} to apply to the text.
     * @param format The {@link Format} to apply to the text.
     * @return The current {@link Message} instance.
     */
    public Message add(String text, TextColor color, Format format) {
        this.messageBuilder.append(
                Component.text(text)
                        .color(color)
                        .decoration(format.getDecoration(), true) // Apply the format
        );
        return this;
    }

    /**
     * Adds a word or phrase to the message with a specific {@link Format}.
     *
     * @param text   The word or phrase to add.
     * @param format The {@link Format} to apply to the text.
     * @return The current {@link Message} instance.
     */
    public Message add(String text, Format format) {
        this.messageBuilder.append(
                Component.text(text)
                        .decoration(format.getDecoration(), true) // Apply the format
        );
        return this;
    }

    /**
     * Adds a word or phrase to the message with RGB color values and a specific {@link Format}.
     *
     * @param text   The word or phrase to add.
     * @param red    The red component of the color (0-255).
     * @param green  The green component of the color (0-255).
     * @param blue   The blue component of the color (0-255).
     * @param format The {@link Format} to apply to the text.
     * @return The current {@link Message} instance.
     */
    public Message add(String text, int red, int green, int blue, Format format) {
        this.messageBuilder.append(
                Component.text(text)
                        .decoration(format.getDecoration(), true) // Apply the format
        );
        return this;
    }

    /**
     * Adds a word or phrase to the message with a specific {@link NamedTextColor} and {@link Format}.
     *
     * @param text   The word or phrase to add.
     * @param color  The {@link NamedTextColor} to apply to the text.
     * @param format The {@link Format} to apply to the text.
     * @return The current {@link Message} instance.
     */
    public Message add(String text, NamedTextColor color, Format format) {
        this.messageBuilder.append(
                Component.text(text).color(color)
        );
        return this;
    }

    /**
     * Sends the message to the CommandSource
     *
     * @param source The CommandSource to send the message to.
     */
    public void sendMessage(CommandSource source) {
        Component component = asComponent();
        switch (messageType) {
            case ACTION_BAR:
                source.sendActionBar(component); // Sends action bar message
                break;
            case CHAT:
            default:
                source.sendMessage(component); // Sends chat message
                break;
        }
    }

    /**
     * Sends the message to the player
     *
     * @param player The player to send the message to.
     */
    public void sendMessage(Player player) {
        Component component = asComponent();
        switch (messageType) {
            case ACTION_BAR:
                player.sendActionBar(component); // Sends action bar message
                break;
            case CHAT:
            default:
                player.sendMessage(component); // Sends chat message
                break;
        }
    }
}

