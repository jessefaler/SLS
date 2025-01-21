package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.sls.utils.Message.Message;

public class Debug {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("debug")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                // -------- What to execute -------- /sls debug
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Message.chat().add("debug mode enabled", NamedTextColor.GRAY).sendMessage(source);
                    return 1;
                });
    }
}
