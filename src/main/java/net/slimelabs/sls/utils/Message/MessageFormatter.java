package net.slimelabs.sls.utils.Message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class MessageFormatter {

    // Method to format the usage with arguments
    public static MessageFormatter usage(String command, String... args) {
        return new MessageFormatter(command, args);
    }

    private final String command;
    private final String[] args;

    // Private constructor to initialize the arguments
    private MessageFormatter(String command, String[] args) {
        this.args = args;
        this.command = command;
    }

    // This method applies the formatter to the message
    public Message usage(Message message) {
        String delimiter = " | ";
        message.add(MessagePreset.SLS);
        message.add(" Usage: ", NamedTextColor.DARK_AQUA);
        message.add(command, NamedTextColor.GRAY);
        message.add(" <", NamedTextColor.DARK_GRAY);
        // Loop through the arguments and add them with the delimiter
        for (int i = 0; i < args.length; i++) {
            message.add(args[i], NamedTextColor.GRAY); // Add the argument
            if (i < args.length - 1) { // Check if it's not the last argument
                message.add(delimiter, NamedTextColor.DARK_GRAY); // Add the delimiter
            }
        }
        message.add(">", NamedTextColor.DARK_GRAY);
        return message;
    }
}
