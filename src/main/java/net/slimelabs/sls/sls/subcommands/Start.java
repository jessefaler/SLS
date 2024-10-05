package net.slimelabs.sls.commands.sls.subcommands;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.List;

public class Start implements SimpleCommand {

    public void execute(SimpleCommand.Invocation invocation) {
        invocation.source().sendMessage(Component.text("Joining server..."));
    }

    public List<String> suggest(SimpleCommand.Invocation invocation) {
        return List.of(); // No suggestions for join command
    }
}
