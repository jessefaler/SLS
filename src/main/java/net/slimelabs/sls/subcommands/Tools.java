package net.slimelabs.sls.commands.sls.subcommands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Tools implements SimpleCommand {
    public void execute(Invocation invocation) {

    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String[] args = invocation.arguments();
        Player player = (Player) invocation.source();
        switch (args.length) {
            case 2:
                return CompletableFuture.completedFuture(List.of("permission-provider", "tests"));
            case 3:
        }
        return CompletableFuture.completedFuture(List.of("war"));
    }
}
