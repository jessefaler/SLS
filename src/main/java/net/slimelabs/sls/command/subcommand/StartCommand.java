package net.slimelabs.sls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.utils.Message.Message;
import net.slimelabs.sls.utils.Message.MessageFormatter;
import net.slimelabs.sls.utils.Message.MessagePreset;
import net.slimelabs.sls.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StartCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("start")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    Message.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    Message.chat()
                            .add(MessageFormatter.usage("/sls start", SLS.REGISTRY_MANAGER.getRegistryNames()))
                            .sendMessage(source);
                    return 1;
                })
                .then(registry());
    }

    // /sls start
    private static RequiredArgumentBuilder<CommandSource, String> registry() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("registry", StringArgumentType.string())
                .suggests((context, builder) -> {
                    Arrays.stream(SLS.REGISTRY_MANAGER.getRegistryNames()).toList().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String registryName = StringArgumentType.getString(context, "registry");
                    Message.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    if (!checkIfRegistryExists(source, registryName)) return 0;
                    Message.chat()
                            .add(MessageFormatter.usage("/sls start " + registryName, "world"))
                            .sendMessage(source);
                    return 0;
                })
                .then(world());
    }

    // /sls start registry
    private static RequiredArgumentBuilder<CommandSource, String> world() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("world", StringArgumentType.string())
                // ----------------------------------- SUGGEST -----------------------------------
                .suggests((context, builder) -> {
                    CommandSource source = context.getSource();
                    String registryName = StringArgumentType.getString(context, "registry");
                    if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registryName)) return Suggestions.empty();

                    //Checks if the current argument is a valid world and if there is a period it appends the joinable worlds to it
                    String world = null;
                    if (context.getArguments().containsKey("world")) {
                        world = StringArgumentType.getString(context, "world");
                        if(world.contains(".")) {
                            if(SLS.REGISTRY_MANAGER.doseWorldExist(world.replace(".", ""))) {
                                ArrayList<String> joinableWorlds = new ArrayList<>();
                                if (source instanceof Player player) {
                                    String playerName = player.getUsername();
                                    joinableWorlds.add(world + playerName);
                                }
                                for(String joinableWorld : joinableWorlds) {
                                    builder.suggest(joinableWorld);
                                }
                                return builder.buildFuture();
                            }
                        }
                    }
                    SLS.REGISTRY_MANAGER.getRegistry(registryName).getWorlds().keySet().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                // ----------------------------------- EXECUTE -----------------------------------
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String registryName = StringArgumentType.getString(context, "registry");
                    if (!checkIfRegistryExists(source, registryName)) return 0;
                    String world = StringArgumentType.getString(context, "world");
                    if(!SLS.REGISTRY_MANAGER.getRegistry(registryName).isWorldPresent(StringUtils.removeTextAfterPeriod(world))) {
                        Message.chat().add(MessagePreset.SLS)
                                .add(" Error: ", NamedTextColor.RED)
                                .add(world, TextColor.color(131, 192, 255))
                                .add(" is not present in the ", NamedTextColor.RED)
                                .add(registryName, TextColor.color(131, 192, 255))
                                .add(" registry.", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    ServerConfiguration serverConfiguration = SLS.REGISTRY_MANAGER.getRegistry(registryName).getWorld(StringUtils.removeTextAfterPeriod(world));
                    if(SLS.SERVER_REGISTRY.containsServer(world)) {
                        Message.chat()
                                .add(MessagePreset.SLS)
                                .add(" Server ", NamedTextColor.RED)
                                .addMiniMessage("<gradient:#83C0FF:#0070C0>" + world.replace("_", " ") + "</gradient>")
                                .add(" is already running", NamedTextColor.RED)
                                .sendMessage(source);
                        return 0;
                    }
                    // Execute the server start on a separate thread so Http Requests don't block the main thread
                    SLS.EXECUTOR.submit(() -> {
                        boolean success = SLS.SERVER_REGISTRY.startServer(world, serverConfiguration, source);
                        if(!success) {
                            Message.chat()
                                    .add("An error occurred while attempting to start the server. Please try again or check the logs for more details.", NamedTextColor.RED)
                                    .sendMessage(source);
                        }
                    });
                    Message.chat()
                            .add(MessagePreset.SLS)
                            .addMiniMessage("<gradient:#58FF7A:#33C0C6> Starting " + world.replace("_", " ") + "...</gradient>")
                            .sendMessage(source);
                    return 1;
                });
    }

    // -------------- Helper Methods and Common Logic -----------------
    private static boolean checkIfRegistryExists(CommandSource source,String registryName) {
        if(!SLS.REGISTRY_MANAGER.doseRegistryExist(registryName)) {
            Message.chat().add(MessagePreset.SLS)
                    .add(" Unknown registry: ", NamedTextColor.AQUA)
                    .add(registryName, NamedTextColor.GRAY)
                    .sendMessage(source);
            Message.chat()
                    .add(MessageFormatter.usage("/sls start", SLS.REGISTRY_MANAGER.getRegistryNames()))
                    .sendMessage(source);
            return false;
        }
        return true;
    }
}
