package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.protoxon.S4J.ServerStatus;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class ListCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("list")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage message = ProtoMessage.chat();
                    message.addMiniMessage("<dark_gray><st>－－－－</st> SERVER LIST <st>－－－－\n</st></dark_gray>");
                    if(SLS.servers.getAll().isEmpty()) {
                        ProtoMessage.chat().add(MessagePreset.SLS).add("No servers found.", NamedTextColor.RED).sendMessage(source);
                        return 1;
                    }

                    for(Server server : SLS.servers.getAll()) {
                        message.add(" - ", NamedTextColor.GOLD);
                        String color = "yellow";
                        if(server.status == ServerStatus.RUNNING) {
                            color = "green";
                        } else if (server.status == ServerStatus.STOPPING || server.status == ServerStatus.OFFLINE) {
                            color = "red";
                        } else if (server.status == ServerStatus.PAUSED) {
                            color = "aqua";
                        }
                        message.addMiniMessage("<hover:show_text:'<" + color + ">" + server.getShortId() + "</" + color + ">'><" + color + ">" + server.name + "</" + color + "></hover>");
                        message.add(": ", NamedTextColor.WHITE);
                        int count = server.getPlayerCount();
                        message.addMiniMessage("<dark_aqua><hover:show_text:'<dark_purple>" + ServerUtils.getPlayers(server) + "</dark_purple>'>" + count + "</hover></dark_aqua>");
                        if(count == 1) {
                            message.addMiniMessage("<dark_aqua><hover:show_text:'<dark_purple>" + ServerUtils.getPlayers(server) + "</dark_purple>'> player</hover></dark_aqua>");
                        } else {
                            message.addMiniMessage("<dark_aqua><hover:show_text:'<dark_purple>" + ServerUtils.getPlayers(server) + "</dark_purple>'> players</hover></dark_aqua>");
                        }
                        message.add("\n");
                    }
                    message.addMiniMessage("<dark_gray><b><st>－－－－－－－－－－－－－－－</st></b></dark_gray>");
                    message.sendMessage(source);
                    return 1;
                });
    }

}
