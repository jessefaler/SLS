package net.slimelabs;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Commands {
    @SuppressWarnings("deprecation")
    public static class sls extends Command implements TabExecutor {
        public sls() {
            super("sls"); // Set the command name
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                sender.sendMessage("§7Incorrect Command Usage");
                return;
            } else if (args.length == 1) {
                if (args[0].equals("debug")) {
                    ProxiedPlayer player = (ProxiedPlayer) sender;
                    if (SLS.PLAYER_CONNECTOR.debugEnabledPlayers.contains(player.getUniqueId())) {
                        sender.sendMessage("§3[SLS] §7debug mode disabled");
                        SLS.PLAYER_CONNECTOR.debugEnabledPlayers.remove(player.getUniqueId());
                        return;
                    }
                    sender.sendMessage("§3[SLS] §7debug mode enabled");
                    SLS.PLAYER_CONNECTOR.debugEnabledPlayers.add(player.getUniqueId());
                    return;
                }
            } else if (args.length == 2) {
                if (args[0].equals("config")) {
                    if (args[1].equals("reload")) {
                        SLS.FILE_HANDLER.reloadMinigamesConfig();
                        sender.sendMessage("Minigames config file reloaded.");
                        return;
                    } else if (args[1].equals("view")) {
                        sender.sendMessage(SLS.MINIGAME_REGISTRY.toString());
                        return;
                    }
                } else if (args[0].equals("join")) {
                    SLS.PLAYER_CONNECTOR.joinServer(args[1].trim().replace("_", " "), sender);
                    return;
                } else if (args[0].equals("shutdown")) {
                    if(args[1].equals("all")) {
                        SLS.SERVER_REGISTRY.shutdownAllServers();
                        sender.sendMessage("§6Shutdown all servers");
                        return;
                    }
                    if (SLS.SERVER_REGISTRY.containsServer(args[1].trim().replace("_", " "))) {
                        SLS.SERVER_REGISTRY.shutdownServer(args[1].trim().replace("_", " "));
                        sender.sendMessage("§7Shutdown server " + args[1].trim().replace("_", " "));
                        return;
                    }
                    sender.sendMessage("§cServer " + args[1].trim().replace("_", " ") + " is not running.");
                    return;
                } else if (args[0].equals("start")) {
                    SLS.SERVER_REGISTRY.startServer(args[1].trim().replace("_", " "));
                    return;
                } else {
                    sender.sendMessage("§cUnknown Command");
                    return;
                }
            } else if (args.length == 3) {
                if (args[0].equals("config")) {
                    if (args[1].equals("view")) {
                        if (SLS.MINIGAME_REGISTRY.containsMinigame(args[2].trim().replace("_", " "))) {
                            sender.sendMessage(SLS.MINIGAME_REGISTRY.viewAMinigamesConfig(args[2].trim().replace("_", " ")));
                            return;
                        }
                        sender.sendMessage("§7Minigame §8" + args[2].trim().replace("_", " ") + "§7 is not in the minigames registry.");
                        return;
                    }
                } else if (args[0].equals("join")) {
                    if(!SLS.MINIGAME_REGISTRY.containsMinigame(args[1].trim().replace("_", " "))) {
                        sender.sendMessage("§7Minigame §8" + args[1].trim().replace("_", " ") + "§7 is not in the minigames registry.");
                        return;
                    }
                    if (args[2].equals("all")) {
                        ProxiedPlayer[] players = ProxyServer.getInstance().getPlayers().toArray(new ProxiedPlayer[0]);
                        for (ProxiedPlayer player : players) {
                            SLS.PLAYER_CONNECTOR.joinServer(args[1].trim().replace("_", " "), player);
                        }
                        return;
                    } else if (args[2].equals("local")) {
                        ProxiedPlayer player = (ProxiedPlayer) sender;
                        String serverName = player.getServer().getInfo().getName();
                        for (ProxiedPlayer targetPlayer : SLS.PROXY.getServerInfo(serverName).getPlayers()) {
                            SLS.PLAYER_CONNECTOR.joinServer(args[1].trim().replace("_", " "), targetPlayer);
                        }
                        return;
                    } else if (SLS.PROXY.getPlayer(args[2]).isConnected()) {
                        SLS.PLAYER_CONNECTOR.joinServer(args[1].trim().replace("_", " "), SLS.PROXY.getPlayer(args[2]));
                        return;
                    }
                    sender.sendMessage("§7Unknown player " + args[2]);
                }
            }
            if (SLS.MINIGAME_REGISTRY.containsMinigame(args[2].trim().replace("_", " "))) {
                sender.sendMessage(SLS.MINIGAME_REGISTRY.viewAMinigamesConfig(args[2].trim().replace("_", " ")));
                return;
            }
            sender.sendMessage("§7Minigame §8" + args[2].trim().replace("_", " ") + "§7 is not in the minigames registry.");
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return new ArrayList<>(Arrays.asList("join", "start", "shutdown", "config", "debug"));
            }
            if (args.length == 2) {
                if (args[0].equals("config")) {
                    return new ArrayList<>(Arrays.asList("reload", "view"));
                } else if (args[0].equals("join")) {
                    return SLS.MINIGAME_REGISTRY.getKeys();
                } else if (args[0].equals("shutdown")) {
                    return SLS.SERVER_REGISTRY.getOnlineMinigameNamesAsList();
                } else if (args[0].equals("start")) {
                    return SLS.MINIGAME_REGISTRY.getKeys();
                }
            }
            if (args.length == 3) {
                if (args[1].equals("view")) {
                    return SLS.MINIGAME_REGISTRY.getKeys();
                }
                if(args[0].equals("join")) {
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
    }
}