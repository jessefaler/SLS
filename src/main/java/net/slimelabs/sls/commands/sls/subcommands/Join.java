package net.slimelabs.sls.commands.sls.subcommands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Join implements SimpleCommand {
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        Player player = (Player) invocation.source();

        switch (args.length) {
            //---------------------- configuration of the /join command ---------------------- \/ 🟪 /join <registry>
            case 1, 2 -> {  // Join command was ran with missing arguments (Send Error)
                player.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage", NamedTextColor.DARK_RED));
                if (player.hasPermission("sls.command.admin")) {
                    player.sendMessage(Component.text("[§aSLS§r] Usage: /sls join [registry] [world] <player>", NamedTextColor.GRAY));
                    return;
                }
                player.sendMessage(Component.text("[§aSLS§r] Usage: /sls join [registry] <minigame>", NamedTextColor.GRAY));
                return;
            }
            //---------------------- configuration of the /join command ---------------------- \/ 🟪 /join registry
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String[] args = invocation.arguments();
        Player player = (Player) invocation.source();
        switch (args.length) {
            case 2:
                return CompletableFuture.completedFuture(List.of(SLS.REGISTRY_MANAGER.getRegistryNames()));
            case 3:
        }
        return CompletableFuture.completedFuture(List.of("war"));
    }
}
