package net.slimelabs.sls.commands.sls;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.slimelabs.sls.commands.sls.subcommands.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SLSCommand implements SimpleCommand {

    Join join = new Join();
    Shutdown shutdown = new Shutdown();
    Start start = new Start();

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("Usage: /sls <join|shutdown>"));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> join.execute(invocation);
            case "shutdown" -> shutdown.execute(invocation);
            default -> source.sendMessage(Component.text("Unknown subcommand. Use /sls <join|shutdown>"));
        }
    }
    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource sender = invocation.source();
        switch (args.length) {
            //-------------------------------- On Parent Command -------------------------------
            case 0, 1 -> { //sls []<--
                return CompletableFuture.completedFuture(sender.hasPermission("sls.command.admin")
                        ? List.of("join", "start", "shutdown", "config", "console", "debug", "info")
                        : List.of("join"));
            }
            //----------------------------------- On Arguments ----------------------------------
            default -> { //sls arg []<--
                return switch (args[0]) {
                    // Call suggestion providers
                    case "join" -> join.suggestAsync(invocation);
                    case "shutdown" -> shutdown.suggestAsync(invocation);
                    case "start" -> start.suggestAsync(invocation);
                    default -> CompletableFuture.completedFuture(Collections.emptyList());
                };
            }
        }
    }
}
