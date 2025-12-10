package net.slimelabs.vsls.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.command.subcommand.*;
import net.slimelabs.vsls.utils.message.ProtoMessage;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;

/**
 * The base command for the SLS system, registered as `/sls`.
 * <p>
 * This class is responsible for registering subcommands under the root `/sls` command.
 * It handles the command registration process and associates subcommands with their respective actions.
 * </p>
 */
public class SLSCommand {
    public static void register() {
        CommandManager commandManager = SLS.proxy.getCommandManager();
        // Create the root command
        LiteralArgumentBuilder<CommandSource> root = LiteralArgumentBuilder.literal("sls");

        root.executes(SLSCommand::handleRootCommand); // Handle the execution of /sls when no arguments are given

        // Register subcommands
        root.then(JoinCommand.register());       // JOIN
        root.then(StartCommand.register());      // START
        root.then(DebugCommand.register());      // DEBUG
        root.then(StopCommand.register());       // SHUTDOWN
        root.then(KillCommand.register());       // KILL
        root.then(ReloadCommand.register());     // RELOAD
        root.then(StatusCommand.register());     // STATUS
        root.then(StatsCommand.register());      // STATS
        //root.then(DeleteCommand.register());   // DELETE
        root.then(ConsoleCommand.register());    // CONSOLE
        root.then(DequeueCommand.register());    // DEQUEUE
        root.then(BlueprintCommand.register());  // BLUEPRINT
        root.then(VersionCommand.register());    // VERSION
        root.then(LogsCommand.register());       // LOGS
        //root.then(FlagsCommand.register());    // FLAGS
        //root.then(MonitorCommand.register());  // MONITOR
        //root.then(ResetCommand.register());    // RESET
        root.then(InfoCommand.register());       // INFO
        //root.then(ConfigCommand.register());   // CONFIG

        // Create the Brigadier command
        BrigadierCommand brigadierCommand = new BrigadierCommand(root);

        // Create command metadata
        CommandMeta commandMeta = commandManager.metaBuilder("sls")
                .plugin(SLS.plugin)
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