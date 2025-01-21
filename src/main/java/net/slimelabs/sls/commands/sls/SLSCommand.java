package net.slimelabs.sls.commands.sls;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.commands.sls.subcommands.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SLSCommand implements SimpleCommand {

    Join join = new Join();
    Shutdown shutdown = new Shutdown();
    Start start = new Start();
    Namespaces namespaces = new Namespaces();

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
            case "namespaces" -> namespaces.execute(invocation);
            case "shutdown" -> shutdown.execute(invocation);
            case "debug" -> toggleDebug(invocation);
            default -> source.sendMessage(Component.text("Unknown subcommand. Use /sls <join|shutdown>"));
        }
    }

    //toggles the debug mode
    public void toggleDebug(Invocation invocation) {
        Player player = (Player) invocation.source();
        if(SLS.DEBUGGER.isDebugPlayer(player)) {
            SLS.DEBUGGER.removeDebugPlayer(player);
            player.sendMessage(Component.text("debug mode disabled", NamedTextColor.GRAY));
            return;
        }
        SLS.DEBUGGER.addDebugPlayer(player);
        player.sendMessage(Component.text("debug mode enabled", NamedTextColor.GRAY));
    }
    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource player = invocation.source();
        switch (args.length) {
            //-------------------------------- On Parent Command -------------------------------
            case 0, 1 -> { //sls []<--
                return CompletableFuture.completedFuture(player.hasPermission("sls.command.admin")
                        ? List.of("join", "start", "shutdown", "config", "console", "debug", "info", "namespaces")
                        : List.of("join", "namespaces"));
            }
            //----------------------------------- On Arguments ----------------------------------
            default -> { //sls arg []<--
                return switch (args[0]) {
                    // Call suggestion providers
                    case "join" -> join.suggestAsync(invocation);
                    case "shutdown" -> shutdown.suggestAsync(invocation);
                    case "start" -> start.suggestAsync(invocation);
                    case "namespaces" -> namespaces.suggestAsync(invocation);
                    default -> CompletableFuture.completedFuture(Collections.emptyList());
                };
            }
        }
    }
}
