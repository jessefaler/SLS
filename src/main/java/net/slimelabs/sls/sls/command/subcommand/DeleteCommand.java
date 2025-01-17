package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.velocitypowered.api.command.CommandSource;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import java.util.concurrent.CompletableFuture;
import static net.slimelabs.sls.api.Api.deleteServer;
import static net.slimelabs.sls.api.Api.getAllServerNames;

public class DeleteCommand {
    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("delete")
                // -------- Permission --------
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Message.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    Message.chat()
                            .add(MessageFormatter.usage("/sls delete", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    // /sls shutdown
    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    CompletableFuture<Suggestions> future = new CompletableFuture<>();
                    // Handle error
                    getAllServerNames().executeAsync(serverNames -> {
                        for (String name : serverNames) {
                            builder.suggest(name);
                        }
                        future.complete(builder.build());
                    }, future::completeExceptionally);
                    return future;
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String serverName = StringArgumentType.getString(context, "server");
                    deleteServer(serverName, source);
                    return 1;
                });
    }
}
