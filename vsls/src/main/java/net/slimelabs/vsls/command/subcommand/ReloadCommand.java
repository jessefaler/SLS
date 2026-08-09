package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.SLSAction;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class ReloadCommand {

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("reload")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.SLS).add("Reloading", NamedTextColor.GRAY).sendMessage(source);
                    SLS.config.reload();
                    reloadSoftware(source);
                    reloadBlueprints(source);
                    return 1;
                })
                .then(type());
    }

    private static RequiredArgumentBuilder<CommandSource, String> type() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("type", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("all");
                    builder.suggest("blueprints");
                    builder.suggest("software");
                    builder.suggest("config");
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String type = StringArgumentType.getString(context, "type");

                    switch (type) {
                        case "all":
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Reloading", NamedTextColor.GRAY).sendMessage(source);
                            SLS.config.reload();
                            reloadSoftware(source);
                            reloadBlueprints(source);
                            return 1;
                        case "blueprints":
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Reloading blueprints", NamedTextColor.GRAY).sendMessage(source);
                            reloadBlueprints(source);
                            return 1;
                        case "software":
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Reloading software configs", NamedTextColor.GRAY).sendMessage(source);
                            reloadSoftware(source);
                            return 1;
                        case "config":
                            SLS.config.reload();
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Reloading config", NamedTextColor.GRAY).sendMessage(source);
                            return 1;
                        default:
                            ProtoMessage.chat().add(MessagePreset.SLS).add("Unknown type " + type, NamedTextColor.DARK_RED).sendMessage(source);
                            ProtoMessage.chat()
                                    .add(MessageFormatter.commandUsage("/sls reload","all", "blueprints", "config"))
                                    .sendMessage(source);
                            return 0;
                    }
                });
    }

    public static void reloadBlueprints(CommandSource source) {
        // Tell Protocube to reload blueprints+mixins, then refresh both local caches
        SLS.api.reloadBlueprints()
                .flatMap(v -> SLS.blueprints.reload())
                .flatMap(v -> SLS.mixins.reload())
                .executeAsync(v -> {
                    SLS.gameTypes.load(SLS.blueprints.getAll());
                    int blueprints = SLS.blueprints.getAll().size();
                    int mixins = SLS.mixins.getAll().size();
                    ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Loaded " + blueprints + " blueprints and " + mixins + " mixins", NamedTextColor.GRAY)
                            .sendMessage(source);
                }, failure -> Log.requestError("Failed to reload blueprints", failure, source));
    }

    public static void reloadSoftware(CommandSource source) {
        // Make a request to the protocube api to tell it to reload its software configs
        SLS.api.reloadSoftwareConfigs().executeAsync(success -> {}, failure -> Log.requestError("Failed to reload software configs", failure, source));
    }

}
