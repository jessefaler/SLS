package net.slimelabs.sls.commands.sls.subcommands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;

import java.util.ArrayList;
import java.util.Collections;
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
            case 2 -> {
                return CompletableFuture.completedFuture(List.of(SLS.REGISTRY_MANAGER.getRegistryNames()));
            }
            case 3 -> {
                //if a "." is added return namespaces
                if (args[2].contains(".")) {
                    List<String> namespaces = new ArrayList<>();  // Use List<String> instead of ArrayList<String>

                    // Loop through owned namespaces and concatenate to args[2], then add to the list
                    args[2] = args[2].substring(0, args[2].indexOf(".") + 1);  // Keep only the part before the dot
                    for (String name : SLS.NAMESPACE_REGISTRY.getOwnedNamespaces(player.getUniqueId())) {
                        namespaces.add(args[2] + name);  // Concatenate args[2] with name
                    }
                    return CompletableFuture.completedFuture(namespaces);
                }
                return CompletableFuture.completedFuture(List.of(SLS.REGISTRY_MANAGER.getRegistry(args[1].trim()).getWorldNames()));
            }
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
