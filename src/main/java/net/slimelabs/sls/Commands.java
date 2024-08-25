package net.slimelabs.sls;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class Commands {

    public static class sls implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();
            String[] args = invocation.arguments();

            if (args.length < 1) {
                sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage", NamedTextColor.DARK_RED));
                if (sender.hasPermission("sls.command.admin")) {//sender has the admin permission so send them all command options
                    sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls <join|start|shutdown|config|console|debug|info>", NamedTextColor.GRAY));
                    return;
                }
                sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls <join> <registry> <world>", NamedTextColor.GRAY));
                return;
            }

            // Check if a player has permission to execute all the following commands other than what is listed here.
            if (!args[0].equals("join") && !sender.hasPermission("sls.command.admin")) {
                sender.sendMessage(Component.text("[§aSLS§r] You do not have permission to execute this command", NamedTextColor.DARK_RED));
                return;
            }

            // Subcommand configuration
            // Each case deals with a different subcommand.
            switch (args[0]) {

                //---------------------- configuration of the /join command ---------------------- \/ 🟪 /join
                case "join" -> {
                    if (args.length == 1) {//command join was entered with no arguments return an error message. /join
                        sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage", NamedTextColor.DARK_RED));
                        if (sender.hasPermission("sls.command.admin")) {
                            sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls join [registry] [world] <player>", NamedTextColor.GRAY));
                            return;
                        }
                        sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls join [registry] <minigame>", NamedTextColor.GRAY));
                        return;
                    }
                    if (args.length == 2) {//join [registry]
                        sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage", NamedTextColor.DARK_RED));
                        if (sender.hasPermission("sls.command.admin")) {
                            sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls join [registry] [world] <player>", NamedTextColor.GRAY));
                            return;
                        }
                        sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls join [registry] <minigame>", NamedTextColor.GRAY));
                        return;
                    }
                    if (args.length == 3) {//join [registry] <minigame>
                        SLS.PLAYER_CONNECTOR.joinServer(format(args[2]), sender);
                        return;
                    }
                    //join [registry] <minigame> <player>
                    if (!sender.hasPermission("sls.command.admin")) {//to join other players requires a permission so check for permission
                        sender.sendMessage(Component.text("[§aSLS§r] You do not have permission to execute this command", NamedTextColor.DARK_RED));
                        return;
                    }
                    if (args[3].equals("all")) {//joins all players on all servers to the minigame
                        for (Player player : SLS.PROXY.getAllPlayers()) {
                            SLS.PLAYER_CONNECTOR.joinServer(format(args[2]), player);
                        }
                        return;
                    }
                    if (args[3].equals("local")) {//joins all players on the command executor server to the minigame
                        Player player = (Player) sender;
                        String serverName = player.getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName()).orElse(null);
                        String minigame = format(args[2]);
                        for (Player targetPlayer : Objects.requireNonNull(SLS.PROXY.getServer(serverName).orElse(null)).getPlayersConnected()) {
                            SLS.PLAYER_CONNECTOR.joinServer(minigame, targetPlayer);
                        }
                        return;
                    }
                    Player playerToJoin = SLS.PROXY.getPlayer(args[3]).orElse(null);
                    if (playerToJoin == null || !playerToJoin.isActive()) {//joins a specific player to the minigame
                        sender.sendMessage(Component.text("[§aSLS§r] player " + args[3] + " was not found", NamedTextColor.DARK_RED));
                        return;
                    }
                    SLS.PLAYER_CONNECTOR.joinServer(format(args[2]), playerToJoin);
                }

                //---------------------- configuration of the /start command ---------------------- \/ 🟪 /start
                case "start" -> {
                    if (args.length < 3) {//command was entered with no arguments return an error message. /start
                        sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage\nUsage: /sls start [registry] <world>", NamedTextColor.DARK_RED));
                        return;
                    }

                    //start the server and send the player with the success message
                    String output = SLS.SERVER_REGISTRY.startServer(format(args[2]));
                    if (output != null) {
                        //the startServer method returns a string of what happened if an error occur otherwise it returns
                        //null so if it is not null an error occurred in the starting process
                        sender.sendMessage(Component.text(output, NamedTextColor.DARK_RED));
                        return;
                    }
                    sender.sendMessage(Component.text("[§aSLS§r] Starting " + format(args[2]), NamedTextColor.GRAY));
                }

                //---------------------- configuration of the /shutdown command ---------------------- \/ 🟪 /shutdown
                case "shutdown" -> {
                    if (args.length == 1) {//command was entered with no arguments return an error message. /shutdown
                        sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage\nUsage: /sls shutdown [registry] <world | all>", NamedTextColor.DARK_RED));
                        return;
                    }
                    if (args[1].equals("all")) {// /shutdown all
                        sender.sendMessage(Component.text("[§aSLS§r] " + SLS.SERVER_REGISTRY.shutdownAllServers(), NamedTextColor.GRAY));
                        return;
                    }
                    sender.sendMessage(Component.text("[§aSLS§r] " + SLS.SERVER_REGISTRY.shutdownServer(format(args[1])), NamedTextColor.GRAY));//shutdown <minigame>
                }

                //---------------------- configuration of the /config command ---------------------- \/ 🟪 /config
                case "config" -> { // /sls config <reload, view> <minigames, adventure, archive> <minigameName or adventuremap names or archivemap>
                    if (args.length <= 2) {//command was entered with no arguments return an error message. /config
                        sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage\nUsage: /sls config <view|reload>", NamedTextColor.DARK_RED));
                        return;
                    }
                    if (args.length == 3) {//sls config <view|reload>
                        if (args[1].equals("reload")) {//reload
                            if(args[2].equals("minigames")) {
                                SLS.FILE_HANDLER.reloadMinigamesConfig();
                                sender.sendMessage(Component.text("[§aSLS§r] Minigames config reloaded.", NamedTextColor.GRAY));
                                return;
                            }
                            if(args[2].equals("adventure_maps")) {
                                SLS.FILE_HANDLER.reloadAdventureMapsConfig();
                                sender.sendMessage(Component.text("[§aSLS§r] Adventure maps config reloaded.", NamedTextColor.GRAY));
                                return;
                            }
                            if(args[2].equals("archives")) {
                                SLS.FILE_HANDLER.reloadArchivesConfig();
                                sender.sendMessage(Component.text("[§aSLS§r] Archive config reloaded.", NamedTextColor.GRAY));
                                return;
                            }
                            if(args[2].equals("all")) {
                                SLS.FILE_HANDLER.reloadMinigamesConfig();
                                SLS.FILE_HANDLER.reloadArchivesConfig();
                                SLS.FILE_HANDLER.reloadArchivesConfig();
                                sender.sendMessage(Component.text("[§aSLS§r] All configs reloaded.", NamedTextColor.GRAY));
                                return;
                            }
                        }
                        if (args[1].equals("view")) {//view
                            if(args[2].equals("minigames")) {
                                sender.sendMessage(Component.text("[§aSLS§r] " + SLS.MINIGAME_REGISTRY.toString(), NamedTextColor.GRAY));
                                return;
                            }
                            if(args[2].equals("adventure_maps")) {
                                sender.sendMessage(Component.text("[§aSLS§r] " + SLS.ADVENTURE_REGISTRY.toString(), NamedTextColor.GRAY));
                                return;
                            }
                            if(args[2].equals("archives")) {
                                sender.sendMessage(Component.text("[§aSLS§r] " + SLS.ARCHIVE_REGISTRY.toString(), NamedTextColor.GRAY));
                                return;
                            }
                            sender.sendMessage(Component.text("[§aSLS§r] No such registry:" + args[2], NamedTextColor.DARK_RED));
                            return;
                        }
                    }
                    if(args.length == 4) {
                        if (args[1].equals("view")) {//view
                            if(args[2].equals("minigames")) {
                                sender.sendMessage(Component.text("[§aSLS§r] " + SLS.MINIGAME_REGISTRY.viewAMinigamesConfig(format(args[3]))));
                                return;
                            }
                            if(args[2].equals("adventure_maps")) {
                                sender.sendMessage(Component.text("[§aSLS§r] " + SLS.ADVENTURE_REGISTRY.viewAAdventureMapsConfig(format(args[3]))));
                                return;
                            }
                            if(args[2].equals("archives")) {
                                sender.sendMessage(Component.text("[§aSLS§r] " + SLS.ARCHIVE_REGISTRY.viewAArchiveConfig(format(args[3]))));
                                return;
                            }
                            sender.sendMessage(Component.text("[§aSLS§r] No such registry:" + args[2], NamedTextColor.DARK_RED));
                        }
                    }
                }

                //---------------------- configuration of the /console command ---------------------- \/ 🟪 /console
                case "console" -> {
                    if (args.length <= 2) {//command was entered with no arguments return an error message. /config
                        sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage\nUsage: /sls console <server> <command>", NamedTextColor.DARK_RED));
                        return;
                    }
                    int size = 2;
                    StringBuilder command = new StringBuilder();
                    while (size < args.length) {//get all arguments after /sls console
                        command.append(args[size]).append(" ");
                        size++;
                    }
                    String commandStr = command.toString().trim();
                    if (commandStr.startsWith("/")) {//remove the slash if it was included in the command
                        commandStr = commandStr.replace("/", "");
                    }
                    sender.sendMessage(Component.text("[§aSLS§r] " + SLS.SERVER_REGISTRY.runACommand(format(args[1]), commandStr), NamedTextColor.GRAY));
                }

                //---------------------- configuration of the /debug command ---------------------- \/ 🟪 /debug
                case "debug" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("[§aSLS§r] This command can only be used by players.", NamedTextColor.DARK_RED));
                        return;
                    }
                    if (SLS.PLAYER_CONNECTOR.debugEnabledPlayers.contains(player.getUniqueId())) {
                        sender.sendMessage(Component.text("[§aSLS§r] debug mode disabled", NamedTextColor.GRAY));
                        SLS.PLAYER_CONNECTOR.debugEnabledPlayers.remove(player.getUniqueId());
                        return;
                    }
                    sender.sendMessage(Component.text("[§aSLS§r] debug mode enabled", NamedTextColor.GRAY));
                    SLS.PLAYER_CONNECTOR.debugEnabledPlayers.add(player.getUniqueId());
                }

                //---------------------- configuration of the /info command ---------------------- \/ 🟪 /info
                case "info" -> {
                    if (args.length == 1) {
                        sender.sendMessage(Component.text("[§aSLS§r] " + SLS.SERVER_REGISTRY.info(), NamedTextColor.GRAY));
                        return;
                    }
                    sender.sendMessage(Component.text("[§aSLS§r] " + SLS.SERVER_REGISTRY.info(format(args[1])), NamedTextColor.GRAY));
                }

                //---------------------- END OF COMMAND CONFIGURATION ---------------------- 🟥
                //---------------------- No arguments matched. Send command usage ----------------------
                default -> {
                    sender.sendMessage(Component.text("[§aSLS§r] Incorrect Command Usage", NamedTextColor.DARK_RED));
                    if (sender.hasPermission("sls.command.admin")) {//sender has the admin permission so send them all command options
                        sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls <join|start|shutdown|config|console|debug|info>", NamedTextColor.GRAY));
                        return;
                    }
                    sender.sendMessage(Component.text("[§aSLS§r] Usage: /sls <join> <minigame>", NamedTextColor.GRAY));
                }
            }
        }

        @Override
        public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
            String[] args = invocation.arguments();
            CommandSource sender = invocation.source();
            if (args.length <= 1) {
                if (!sender.hasPermission("sls.command.admin")) {
                    //returns commands that only players without the sls.command.admin can use
                    return CompletableFuture.completedFuture(List.of("join"));
                }
                //returns all commands players with the sls.command.admin can use
                return CompletableFuture.completedFuture(List.of("join", "start", "shutdown", "config", "console", "debug", "info"));
            }
            if (args.length == 2) {
                switch (args[0]) {
                    case "config" -> {
                        return CompletableFuture.completedFuture(List.of("view", "reload"));
                    }
                    case "join", "start" -> {
                        return CompletableFuture.completedFuture(List.of("minigame", "adventure_map", "archive"));
                    }
                    case "shutdown" -> {
                        return CompletableFuture.completedFuture(SLS.SERVER_REGISTRY.getOnlineMinigameNamesAsList(true));
                    }
                    case "console", "info" -> {
                        return CompletableFuture.completedFuture(SLS.SERVER_REGISTRY.getOnlineMinigameNamesAsList(false));
                    }
                }
            }
            if (args.length == 3) {
                if (args[1].equals("view")) {
                    return CompletableFuture.completedFuture(List.of("minigames", "adventure_maps", "archives"));
                }
                if (args[1].equals("reload")) {
                    return CompletableFuture.completedFuture(List.of("minigames", "adventure_maps", "archives", "all"));
                }

                if (args[0].equals("join") || args[0].equals("start")) {
                    if(args[1].equals("minigame")) {
                        return CompletableFuture.completedFuture(SLS.MINIGAME_REGISTRY.getKeys());
                    }
                    if(args[1].equals("adventure_map")) {
                        return CompletableFuture.completedFuture(SLS.ADVENTURE_REGISTRY.getKeys());
                    }
                    if(args[1].equals("archive")) {
                        return CompletableFuture.completedFuture(SLS.ARCHIVE_REGISTRY.getKeys());
                    }
                }
            }
            if(args.length == 4) {
                if (args[1].equals("view")) {
                    if(args[2].equals("minigames")) {
                        return CompletableFuture.completedFuture(SLS.MINIGAME_REGISTRY.getKeys());
                    }
                    if(args[2].equals("adventure_maps")) {
                        return CompletableFuture.completedFuture(SLS.ADVENTURE_REGISTRY.getKeys());
                    }
                    if(args[2].equals("archives")) {
                        return CompletableFuture.completedFuture(SLS.ARCHIVE_REGISTRY.getKeys());
                    }
                }
                if (args[0].equals("join")) {
                    if (!sender.hasPermission("sls.command.admin")) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                    Collection<Player> players = SLS.PROXY.getAllPlayers();
                    List<String> onlinePlayers = new ArrayList<>();

                    onlinePlayers.add("all");
                    onlinePlayers.add("local");

                    for (Player player : players) {
                        onlinePlayers.add(player.getUsername());
                    }

                    return CompletableFuture.completedFuture(onlinePlayers);
                }
            }
            return CompletableFuture.completedFuture(List.of());
        }

        // Replaces the underscore in the string with a space and trims it, so it will match the minigame name in the minigames registry
        public String format(String name) {
            return name.toLowerCase().trim().replace("_", " ");
        }
    }
}