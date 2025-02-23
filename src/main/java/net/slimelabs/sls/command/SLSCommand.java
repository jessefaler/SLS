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
import net.slimelabs.sls.server.WatcherService;
import net.slimelabs.sls.utils.Message.ProtoMessage;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;

/**
 * The base command for the SLS system, registered as `/sls`.
 * <p>
 * This class is responsible for registering subcommands under the root `/sls` command.
 * It handles the command registration process and associates subcommands with their respective actions.
 * </p>
 */
public class SLSCommand {
    public static void register() {
        CommandManager commandManager = SLS.PROXY.getCommandManager();
        // Create the root command
        LiteralArgumentBuilder<CommandSource> root = LiteralArgumentBuilder.literal("sls");

        root.executes(SLSCommand::handleRootCommand); // Handle the execution of /sls when no arguments are given

        // Register subcommands
        root.then(JoinCommand.register());     // JOIN
        root.then(StartCommand.register());    // START
        root.then(Debug.register());           // DEBUG
        root.then(ShutdownCommand.register()); // SHUTDOWN
        root.then(DeleteCommand.register());   // DELETE
        root.then(ConsoleCommand.register());  // CONSOLE
        root.then(DequeueCommand.register());  // DEQUEUE
        root.then(VersionCommand.register());  // VERSION
        root.then(FlagsCommand.register());    // FLAGS
        root.then(MonitorCommand.register());  // MONITOR
        root.then(ResetCommand.register());    // RESET
        root.then(InfoCommand.register());     // INFO
        root.then(ConfigCommand.register());   // CONFIG

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

        ProtoMessage.chat()
                .add(MessagePreset.SLS)
                .add(" Incorrect Command Usage!", TextColor.color(237, 67, 55))
                .sendMessage(source);

        if (source.hasPermission("sls.command.admin")) {
            ProtoMessage.chat()
                    .add(MessageFormatter.commandUsage("/sls", "join", "start", "shutdown", "config", "console", "debug", "info"))
                    .sendMessage(source);
            return 1;
        } else {
            ProtoMessage.chat()
                    .add(MessageFormatter.commandUsage("/sls", "join", "info"))
                    .sendMessage(source);
        }
        return 0;
    }
}
