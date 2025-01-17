package net.slimelabs.sls.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.command.subcommand.*;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;

public class SLSCommand {
    public static void register() {
        CommandManager commandManager = SLS.PROXY.getCommandManager();
        // Create the root command
        LiteralArgumentBuilder<CommandSource> root = LiteralArgumentBuilder.literal("sls");

        root.executes(SLSCommand::handleRootCommand);

        // Register subcommands
        root.then(JoinCommand.register());
        root.then(StartCommand.register());
        root.then(Debug.register());
        root.then(ShutdownCommand.register());
        root.then(DeleteCommand.register());
        root.then(ConsoleCommand.register());
        root.then(DequeueCommand.register());

        // Create the Brigadier command
        BrigadierCommand brigadierCommand = new BrigadierCommand(root);

        // Create command metadata
        CommandMeta commandMeta = commandManager.metaBuilder("sls")
                .plugin(SLS.PLUGIN)
                .build();

        // Register the command
        commandManager.register(commandMeta, brigadierCommand);
    }

    private static int handleRootCommand(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();

        Message.chat()
                .add(MessagePreset.SLS)
                .add(" Incorrect Command Usage!", TextColor.color(237, 67, 55))
                .sendMessage(source);

        if (source.hasPermission("sls.command.admin")) {
            Message.chat()
                    .add(MessageFormatter.usage("/sls", "join", "start", "shutdown", "config", "console", "debug", "info"))
                    .sendMessage(source);
            return 1;
        } else {
            Message.chat()
                    .add(MessageFormatter.usage("/sls", "join", "info"))
                    .sendMessage(source);
        }
        return 0;
    }
}
