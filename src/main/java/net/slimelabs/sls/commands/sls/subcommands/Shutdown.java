package net.slimelabs.sls.commands.sls.subcommands;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.List;

public class Shutdown implements SimpleCommand {

    public void execute(SimpleCommand.Invocation invocation) {
        invocation.source().sendMessage(Component.text("Joining server..."));
    }
}
