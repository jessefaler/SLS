package net.slimelabs.sls.commands.sls.subcommands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.namespaces.Namespace;
import net.slimelabs.sls.utils.UUIDFetcher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Namespaces implements SimpleCommand {

    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        Player player = (Player) invocation.source();

        if (args.length < 2) {
            player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces <create|edit|view>", NamedTextColor.RED));
            return;
        }

        String action = args[1];

        switch (args.length) {
            case 2 -> {
                switch (action) {
                    case "view" -> {
                        player.sendMessage(SLS.NAMESPACE_REGISTRY.getOwnedNamespacesString(player.getUniqueId()));
                        player.sendMessage(SLS.NAMESPACE_REGISTRY.getManagedNamespacesString(player.getUniqueId()));
                    }
                    case "create", "edit" -> {
                        player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces " + action + " <name>", NamedTextColor.RED));
                    }
                    default -> {
                        player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces <create|edit|view>", NamedTextColor.RED));
                    }
                }
            }

            case 3 -> {
                if ("view".equals(action)) {
                    Namespace namespace = SLS.NAMESPACE_REGISTRY.getNamespace(args[2]);
                    player.sendMessage(Component.text(namespace.name + ": ", NamedTextColor.DARK_GREEN));
                    player.sendMessage(Component.text("Owner: ", NamedTextColor.DARK_AQUA).append(Component.text(namespace.ownerName, NamedTextColor.GOLD)));
                    if (namespace.isPrivate) {
                        player.sendMessage(Component.text("Privacy: ", NamedTextColor.DARK_AQUA).append(Component.text("private", NamedTextColor.GOLD)));
                    } else {
                        player.sendMessage(Component.text("Privacy: ", NamedTextColor.DARK_AQUA).append(Component.text("public", NamedTextColor.GOLD)));
                    }
                    player.sendMessage(namespace.getManagersAsString());
                    return;
                }
                if ("create".equals(action)) {
                    String name = args[2].trim();
                    boolean success = SLS.NAMESPACE_REGISTRY.createNamespace(name, player.getUniqueId(), player.getUsername());
                    if (success) {
                        player.sendMessage(Component.text("Created namespace: ", NamedTextColor.DARK_AQUA).append(Component.text(name, NamedTextColor.GOLD)));
                    } else {
                        player.sendMessage(Component.text("Namespace " + name + " already exists", NamedTextColor.RED));
                    }
                    return;
                } else if ("edit".equals(action)) {
                    player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces edit <namespace> <add-manager|rename|delete>", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces <create|edit|view>", NamedTextColor.RED));
            }

            case 4 -> {
                if ("view".equals(action)) {
                    Namespace namespace = SLS.NAMESPACE_REGISTRY.getNamespace(args[2]);
                    if (namespace == null) {
                        player.sendMessage(Component.text("Namespace " + args[2] + " does not exist", NamedTextColor.RED));
                        return;
                    }
                    switch (args[3]) {
                        case "managers" -> {
                            player.sendMessage(namespace.getManagersAsString());
                            return;
                        }
                        case "owner" -> {
                            player.sendMessage(Component.text("Owner: ", NamedTextColor.DARK_AQUA).append(Component.text(namespace.ownerName, NamedTextColor.GOLD)));
                            return;
                        }
                        case "privacy" -> {
                            if (namespace.isPrivate) {
                                player.sendMessage(Component.text("Privacy: ", NamedTextColor.DARK_AQUA).append(Component.text("private", NamedTextColor.GOLD)));
                                return;
                            }
                            player.sendMessage(Component.text("Privacy: ", NamedTextColor.DARK_AQUA).append(Component.text("public", NamedTextColor.GOLD)));
                            return;
                        }
                    }
                }
                if ("edit".equals(action)) {
                    if (SLS.NAMESPACE_REGISTRY.isOwner(player, args[2])) {
                        player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces edit " + args[2] + " <add-manager|rename|delete>", NamedTextColor.RED));
                        return;
                    }
                    player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces edit " + args[2] + " <add-manager>", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces <create|edit|view>", NamedTextColor.RED));
            }

            case 5 -> {
                if ("edit".equals(action)) {
                    Namespace namespace = SLS.NAMESPACE_REGISTRY.getNamespace(args[2]);
                    if (namespace == null) {
                        player.sendMessage(Component.text("Namespace " + args[2] + " does not exist", NamedTextColor.RED));
                        return;
                    }

                    switch (args[3]) {
                        case "add-manager" -> {
                            Optional<Player> manager = SLS.PROXY.getPlayer(args[4]);
                            if (manager.isEmpty()) {
                                if (SLS.DEBUGGER.isDebugPlayer(player)) {
                                    player.sendMessage(Component.text("Fetching UUID from Mojang API ", NamedTextColor.GRAY));
                                }
                                Optional<UUID> uuid = UUIDFetcher.getUUIDFromUsername(args[4]);
                                if (uuid.isEmpty()) {
                                    player.sendMessage(Component.text("Could not find player " + args[4], NamedTextColor.RED));
                                } else {
                                    if (namespace.hasManager(uuid.get())) {
                                        player.sendMessage(Component.text(args[4] + " is already a manager", NamedTextColor.RED));
                                        return;
                                    }
                                    player.sendMessage(Component.text("Added manager: " + args[4], NamedTextColor.DARK_AQUA));
                                    namespace.addManager(args[4], uuid.get());
                                }
                            } else {
                                if (namespace.hasManager(manager.get().getUniqueId())) {
                                    player.sendMessage(Component.text(args[4] + " is already a manager", NamedTextColor.RED));
                                    return;
                                }
                                player.sendMessage(Component.text("Added manager: " + args[4], NamedTextColor.DARK_AQUA));
                                namespace.addManager(args[4], manager.get().getUniqueId());
                            }
                            return;
                        }
                        case "remove-manager" -> {
                            if(namespace.hasManager(args[4])) {
                                if(namespace.ownerName.equals(args[4])) {
                                    player.sendMessage(Component.text("Cannot remove " + args[4] + " as they are the owner of the namespace", NamedTextColor.RED));
                                    return;
                                }
                                namespace.managers.remove(args[4]);
                                player.sendMessage(Component.text("Removed manager " + args[4], NamedTextColor.DARK_AQUA));
                                return;
                            }
                            //this only gets ran if a user changed their name from when they were added as a manager
                            Optional<Player> manager = SLS.PROXY.getPlayer(args[4]);
                            if (manager.isEmpty()) {
                                if (SLS.DEBUGGER.isDebugPlayer(player)) {
                                    player.sendMessage(Component.text("Fetching UUID from Mojang API ", NamedTextColor.GRAY));
                                }
                                Optional<UUID> uuid = UUIDFetcher.getUUIDFromUsername(args[4]);
                                if (uuid.isEmpty()) {
                                    player.sendMessage(Component.text("Could not find player " + args[4], NamedTextColor.RED));
                                } else {
                                    if(namespace.hasManager(uuid.get())) {
                                        if(namespace.owner.equals(uuid.get())) {
                                            player.sendMessage(Component.text("Cannot remove " + args[4] + " as they are the owner of the namespace", NamedTextColor.RED));
                                            return;
                                        }
                                        namespace.removeManager(uuid.get());
                                        player.sendMessage(Component.text("Removed manager " + args[4], NamedTextColor.DARK_AQUA));
                                        return;
                                    }
                                    player.sendMessage(Component.text(args[4] + " is not a manager", NamedTextColor.RED));
                                }
                            } else {
                                if(namespace.hasManager(manager.get().getUniqueId())) {
                                    if(namespace.owner.equals(manager.get().getUniqueId())) {
                                        player.sendMessage(Component.text("Cannot remove " + args[4] + " they are the owner of the namespace", NamedTextColor.RED));
                                        return;
                                    }
                                    namespace.removeManager(manager.get().getUniqueId());
                                    player.sendMessage(Component.text("Removed manager " + args[4], NamedTextColor.DARK_AQUA));
                                    return;
                                }
                                player.sendMessage(Component.text(args[4] + " is not a manager", NamedTextColor.RED));
                            }
                            return;
                        }
                        case "rename" -> {
                            boolean success = SLS.NAMESPACE_REGISTRY.renameNamespace(args[4], namespace.name);
                            if(success) {
                                player.sendMessage(Component.text("Namespace renamed to " + namespace.name, NamedTextColor.DARK_AQUA));
                                return;
                            }
                            player.sendMessage(Component.text(args[4] + " is in use by another namespace.", NamedTextColor.RED));
                            return;
                        }
                        case "delete" -> {
                            SLS.NAMESPACE_REGISTRY.deleteNamespace(namespace.name);
                            player.sendMessage(Component.text("Deleted namespace ", NamedTextColor.DARK_GREEN).append(Component.text(namespace.name, NamedTextColor.RED)));
                            return;
                        }
                        case "privacy" -> {
                            if(args[4].equals("private")) {
                                namespace.isPrivate = true;
                                player.sendMessage(Component.text("Namespace " , NamedTextColor.DARK_AQUA).append(Component.text(namespace.name, NamedTextColor.GOLD)).append(Component.text(" is now ", NamedTextColor.DARK_AQUA).append(Component.text("private", NamedTextColor.RED))));
                                return;
                            }
                            if(args[4].equals("public")) {
                                namespace.isPrivate = true;
                                player.sendMessage(Component.text("Namespace " , NamedTextColor.DARK_AQUA).append(Component.text(namespace.name, NamedTextColor.GOLD)).append(Component.text(" is now ", NamedTextColor.DARK_AQUA).append(Component.text("public", NamedTextColor.DARK_GREEN))));
                                return;
                            }
                            player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces edit " + args[2] + " privacy <public|private>", NamedTextColor.RED));
                            return;
                        }
                        default -> {
                            player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces edit " + args[2] + " <add-manager|rename|delete>", NamedTextColor.RED));
                            return;
                        }
                    }
                }
                player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces <create|edit|view>", NamedTextColor.RED));
            }

            default -> {
                player.sendMessage(Component.text("Incorrect Command Usage\nUsage: /sls namespaces <create|edit|view>", NamedTextColor.RED));
            }
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String[] args = invocation.arguments();
        Player player = (Player) invocation.source();
        return switch (args.length) {
            case 2 -> CompletableFuture.completedFuture(List.of("create", "edit", "view"));
            case 3 -> {
                if(args[1].equals("edit") || args[1].equals("view")) {
                    yield CompletableFuture.completedFuture(SLS.NAMESPACE_REGISTRY.getManagedNamespaces(player.getUniqueId()));
                }
                yield CompletableFuture.completedFuture(Collections.emptyList());
            }
            case 4 -> {
                if(args[1].equals("edit")) {
                    if(SLS.NAMESPACE_REGISTRY.isOwner(player, args[2].trim())) {
                        yield  CompletableFuture.completedFuture(List.of("add-manager", "rename", "delete", "remove-manager", "privacy"));
                    }
                    yield CompletableFuture.completedFuture(List.of("add-manager"));
                }
                if(args[1].equals("view")) {
                    yield CompletableFuture.completedFuture(List.of("managers", "owner", "privacy"));
                }
                yield CompletableFuture.completedFuture(Collections.emptyList());
            }
            case 5 -> {
                if(args[3].equals("add-manager") || args[3].equals("remove-manager") && args[1].equals("edit")) {
                    yield CompletableFuture.completedFuture(SLS.PROXY.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList()));
                }
                if(args[3].equals("privacy") && args[1].equals("edit")) {
                    yield CompletableFuture.completedFuture(List.of("public", "private"));
                }
                yield CompletableFuture.completedFuture(Collections.emptyList());
            }
            default -> CompletableFuture.completedFuture(Collections.emptyList());
        };
    }
}
