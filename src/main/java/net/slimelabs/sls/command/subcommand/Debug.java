package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.utils.Message.ProtoMessage;

public class Debug {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("debug")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                // -------- What to execute -------- /sls debug
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add("debug mode enabled", NamedTextColor.GRAY).sendMessage(source);
                    return 1;
                });
    }
}
