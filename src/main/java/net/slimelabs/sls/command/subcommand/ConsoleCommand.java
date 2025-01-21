package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.Message.MessagePreset;

import static net.slimelabs.sls.api.API.deleteServer;

public class ConsoleCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("console")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    sendUsage(source); // Incorrect command usage send command usage
                    return 1;
                })
                .then(server());
    }

    // /sls shutdown
    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SLS.SERVER_REGISTRY.getServerNames().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    sendUsage(source); // Incorrect command usage send command usage
                    return 1;
                })
                .then(console());
    }

    private static RequiredArgumentBuilder<CommandSource, String> console() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("command", StringArgumentType.greedyString())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String serverName = StringArgumentType.getString(context, "server");
                    String command = StringArgumentType.getString(context, "command");
                    SLS.SERVER_REGISTRY.sendCommand(command, serverName, source);
                    return 1;
                });
    }

    private static void sendUsage(CommandSource source) {
        ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
        ProtoMessage.chat()
                .add("Usage: ", NamedTextColor.DARK_AQUA)
                .add("/sls console ", NamedTextColor.GRAY)
                .add("<", NamedTextColor.DARK_GRAY)
                .add("server", NamedTextColor.GRAY)
                .add("> ", NamedTextColor.DARK_GRAY)
                .add("<", NamedTextColor.DARK_GRAY)
                .add("world", NamedTextColor.GRAY)
                .add("> ", NamedTextColor.DARK_GRAY)
                .sendMessage(source);
    }
}
