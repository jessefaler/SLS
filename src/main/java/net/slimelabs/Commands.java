package net.slimelabs;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Commands {

    public static class sls extends Command implements TabExecutor {
        public sls() {
            super("sls"); // Set the command name
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(new TextComponent("§cIncorrect Command Usage"));
                if(sender.hasPermission("sls.command.admin")) {//sender has the admin permission so send them all command options
                    sender.sendMessage(new TextComponent("§7Usage: /sls §8<§7join§8|§7start§8|§7shutdown§8|§7config§8|§7console§8|§7debug§8|§7info§8>"));
                    return;
                }
                sender.sendMessage(new TextComponent("§7Usage: /sls <join> <minigame>"));
                return;
            }

            //check if a player has permission to execute all the following commands other than what is listed here.
            if(!args[0].equals("join") && !sender.hasPermission("sls.command.admin")) {
                sender.sendMessage(new TextComponent("§cYou do not have permission to execute this command"));
                return;
            }

            //subcommand configuration
            //each case deals with a different subcommand.
            switch (args[0]) {

                //---------------------- configuration of the /join command ---------------------- \/ 🟪 /join
                case "join" -> {
                    if (args.length == 1) {//command join was entered with no arguments return an error message. /join
                        sender.sendMessage(new TextComponent("§cIncorrect Command Usage"));
                        if (sender.hasPermission("sls.command.admin")) {
                            sender.sendMessage(new TextComponent("§7Usage: /sls join [minigame] <player>"));
                            return;
                        }
                        sender.sendMessage(new TextComponent("§7Usage: /sls join <minigame>"));
                        return;
                    }
                    if (args.length == 2) {//join <minigame>
                        SLS.PLAYER_CONNECTOR.joinServer(format(args[1]), sender);
                        return;
                    }
                    //join <minigame> <player>
                    if (!sender.hasPermission("sls.command.admin")) {//to join other players requires a permission so check for permission
                        sender.sendMessage(new TextComponent("§cYou do not have permission to execute this command"));
                        return;
                    }
                    if(args[2].equals("all")) {//joins all players on all servers to the minigame
                        ProxiedPlayer[] players = ProxyServer.getInstance().getPlayers().toArray(new ProxiedPlayer[0]);
                        for (ProxiedPlayer player : players) {
                            if(!player.getServer().getInfo().getName().equals(args[1].toLowerCase().trim())) {//connects players who are not already on the server to the server.
                                SLS.PLAYER_CONNECTOR.joinServer(format(args[1]), player);
                            }
                        }
                        return;
                    }
                    if(args[2].equals("local")) {//joins all players on the command executor server to the minigame
                        ProxiedPlayer player = (ProxiedPlayer) sender;
                        String serverName = player.getServer().getInfo().getName();
                        String minigame = format(args[1]);
                        for (ProxiedPlayer targetPlayer : SLS.PROXY.getServerInfo(serverName).getPlayers()) {
                            SLS.PLAYER_CONNECTOR.joinServer(minigame, targetPlayer);
                        }
                        return;
                    }
                    ProxiedPlayer playerToJoin = SLS.PROXY.getPlayer(args[2]);
                    if (playerToJoin == null || !playerToJoin.isConnected()) {//joins a specific player to the minigame
                        sender.sendMessage(new TextComponent("§cplayer " + args[2] + " was not found"));
                        return;
                    }
                    SLS.PLAYER_CONNECTOR.joinServer(format(args[1]), playerToJoin);
                }

                //---------------------- configuration of the /start command ---------------------- \/ 🟪 /start
                case "start" -> {
                    if (args.length == 1) {//command was entered with no arguments return an error message. /start
                        sender.sendMessage(new TextComponent("§cIncorrect Command Usage\n§7Usage: /sls start <minigame>"));
                        return;
                    }
                    //start the server and send the player with the success message
                    String output = SLS.SERVER_REGISTRY.startServer(format(args[1]));
                    if(output != null) {
                        //the start method returns a string of what happened if an error occur otherwise it returns
                        //null so if it is not null an error occurred in the starting process
                        sender.sendMessage(new TextComponent(SLS.SERVER_REGISTRY.startServer(format(args[1]))));//start <minigame>
                        return;
                    }
                    sender.sendMessage(new TextComponent("§7Starting " + format(args[1])));
                }

                //---------------------- configuration of the /shutdown command ---------------------- \/ 🟪 /shutdown
                case "shutdown" -> {
                    if (args.length == 1) {//command was entered with no arguments return an error message. /shutdown
                        sender.sendMessage(new TextComponent("§cIncorrect Command Usage\n§7Usage: /sls shutdown <minigame|all>"));
                        return;
                    }
                    if(args[1].equals("all")) {// /shutdown all
                        sender.sendMessage(new TextComponent(SLS.SERVER_REGISTRY.shutdownAllServers()));
                        return;
                    }
                    sender.sendMessage(new TextComponent(SLS.SERVER_REGISTRY.shutdownServer(format(args[1]))));//shutdown <minigame>
                }

                //---------------------- configuration of the /config command ---------------------- \/ 🟪 /config
                case "config" -> {
                    if (args.length == 1) {//command was entered with no arguments return an error message. /config
                        sender.sendMessage(new TextComponent("§cIncorrect Command Usage\n§7Usage: /sls config <view|reload>"));
                        return;
                    }
                    if (args.length == 2) {//sls config <view|reload>
                        if(args[1].equals("reload")) {//reload
                            SLS.FILE_HANDLER.reloadMinigamesConfig();
                            sender.sendMessage(new TextComponent("§7Minigames config reloaded."));
                            return;
                        }
                        if(args[1].equals("view")) {//view
                            sender.sendMessage(new TextComponent(SLS.MINIGAME_REGISTRY.toString()));
                            return;
                        }
                    }
                    sender.sendMessage(new TextComponent(SLS.MINIGAME_REGISTRY.viewAMinigamesConfig(format(args[2]))));//view minigame
                }

                //---------------------- configuration of the /console command ---------------------- \/ 🟪 /console
                case "console" -> {
                    if (args.length <= 2) {//command was entered with no arguments return an error message. /config
                        sender.sendMessage(new TextComponent("§cIncorrect Command Usage\n§7Usage: /sls console <server> <command>"));
                        return;
                    }
                    int size = 2;
                    String command = "";
                    while(size < args.length) {//get all arguments after /sls console
                        command += args[size] + " ";
                        size++;
                    }
                    if(command.startsWith("/")) {//remove the slash if it was included in the command
                        command = command.replace("/", "");
                    }
                    sender.sendMessage(new TextComponent(SLS.SERVER_REGISTRY.runACommand(format(args[1]), command.trim())));
                }

                //---------------------- configuration of the /debug command ---------------------- \/ 🟪 /debug
                case "debug" -> {
                    ProxiedPlayer player = (ProxiedPlayer) sender;
                    if (SLS.PLAYER_CONNECTOR.debugEnabledPlayers.contains(player.getUniqueId())) {
                        sender.sendMessage(new TextComponent("§3[SLS] §7debug mode disabled"));
                        SLS.PLAYER_CONNECTOR.debugEnabledPlayers.remove(player.getUniqueId());
                        return;
                    }
                    sender.sendMessage(new TextComponent("§3[SLS] §7debug mode enabled"));
                    SLS.PLAYER_CONNECTOR.debugEnabledPlayers.add(player.getUniqueId());
                }

                //---------------------- configuration of the /info command ---------------------- \/ 🟪 /info
                case "info" -> {
                    if (args.length == 1) {
                        sender.sendMessage(new TextComponent(SLS.SERVER_REGISTRY.info()));
                        return;
                    }
                    sender.sendMessage(new TextComponent(SLS.SERVER_REGISTRY.info(format(args[1]))));
                }

                //---------------------- END OF COMMAND CONFIGURATION ---------------------- 🟥
                //---------------------- No arguments matched. Send command usage ----------------------
                default -> {
                    sender.sendMessage(new TextComponent("§cIncorrect Command Usage"));
                    if(sender.hasPermission("sls.command.admin")) {//sender has the admin permission so send them all command options
                        sender.sendMessage(new TextComponent("§7Usage: /sls §8<§7join§8|§7start§8|§7shutdown§8|§7config§8|§7console§8|§7debug§8|§7info§8>"));
                        return;
                    }
                    sender.sendMessage(new TextComponent("§7Usage: /sls <join> <minigame>"));
                }
            }
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                if(!sender.hasPermission("sls.command.admin")) {
                    //returns commands that only players without the sls.command.admin can use
                    return new ArrayList<>(List.of("join"));
                }
                //returns all commands players with the sls.command.admin can use
                return new ArrayList<>(Arrays.asList("join", "start", "shutdown", "config", "console", "debug", "info"));
            }
            if (args.length == 2) {
                if (args[0].equals("config")) {
                    return new ArrayList<>(Arrays.asList("reload", "view"));
                } else if (args[0].equals("join")) {
                    return SLS.MINIGAME_REGISTRY.getKeys();
                } else if (args[0].equals("shutdown")) {
                    return SLS.SERVER_REGISTRY.getOnlineMinigameNamesAsList(true);
                } else if (args[0].equals("start")) {
                    return SLS.MINIGAME_REGISTRY.getKeys();
                } else if (args[0].equals("console")) {
                    return SLS.SERVER_REGISTRY.getOnlineMinigameNamesAsList(false);
                } else if (args[0].equals("info")) {
                    return SLS.SERVER_REGISTRY.getOnlineMinigameNamesAsList(false);
                }
            }
            if (args.length == 3) {
                if (args[1].equals("view")) {
                    return SLS.MINIGAME_REGISTRY.getKeys();
                }
                if(args[0].equals("join")) {
                    if(!sender.hasPermission("sls.command.admin")) {
                        return new ArrayList<>();
                    }

                    Collection<ProxiedPlayer> players = ProxyServer.getInstance().getPlayers();
                    List<String> onlinePlayers = new ArrayList<>();

                    onlinePlayers.add("all");
                    onlinePlayers.add("local");

                    for (ProxiedPlayer player : players) {
                        onlinePlayers.add(player.getName());
                    }

                    return onlinePlayers;
                }
            }
            return new ArrayList<>();
        }

        //replaces the underscore in the string with a space and trims it, so it will match the minigame name in the minigames registry
        public String format(String name) {
            return name.toLowerCase().trim().replace("_", " ");
        }
    }
}