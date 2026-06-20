package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.protoxon.S4J.InstallPhase;
import com.protoxon.S4J.requests.PaginationAction;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.server.Server;
import net.slimelabs.vsls.utils.ServerUtils;
import net.slimelabs.vsls.utils.TimeUtils;
import net.slimelabs.vsls.utils.message.CommandMessageParts;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

import java.time.Instant;

public class InstallCommand {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_LINES_PER_PAGE = 50;
    private static final int MAX_LINES_PER_PAGE = 1000;

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("install")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls install", "info", "logs", "reinstall"))
                            .sendMessage(source);
                    return 1;
                })
                .then(info())
                .then(logs())
                .then(reinstall());
    }

    // --- info ---

    private static LiteralArgumentBuilder<CommandSource> info() {
        return LiteralArgumentBuilder.<CommandSource>literal("info")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if (!(source instanceof Player player)) {
                        Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                        return 0;
                    }
                    Server server = ServerUtils.getServer(player);
                    if (server != null) {
                        showInstallInfo(server, source);
                    } else {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("Server " + ServerUtils.getServerName(player) + " is not an SLS server", NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 1;
                })
                .then(infoServer());
    }

    private static RequiredArgumentBuilder<CommandSource, String> infoServer() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("this");
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    Server server = resolveServer(source, id);
                    if (server != null) {
                        showInstallInfo(server, source);
                    } else if (!id.equals("this")) {
                        ProtoMessage.chat()
                                .add(MessagePreset.SLS)
                                .add("No such server " + id, NamedTextColor.RED)
                                .sendMessage(source);
                    }
                    return 0;
                });
    }

    private static void showInstallInfo(Server server, CommandSource source) {
        server.getInstallInfo().executeAsync(info -> {
            String phaseColor = phaseColor(info.getPhase());
            String containerName = orUnknown(info.getContainerName());
            String containerId = orUnknown(info.getContainerId());
            String containerStatus = orUnknown(info.getStatus());
            String exitCode = info.getExitCode() != null ? String.valueOf(info.getExitCode()) : "Unknown";
            String startedAt = formatInstant(info.getStartedAt());
            String finishedAt = formatInstant(info.getFinishedAt());
            String failureReason = info.getFailureReason();

            StringBuilder message = new StringBuilder();
            message.append("<dark_aqua>Install</dark_aqua> <dark_gray>(</dark_gray><dark_aqua>")
                    .append(server.getCompositeId())
                    .append("</dark_aqua><dark_gray>)</dark_gray>:\n")
                    .append("<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－\n</st></b></dark_gray>")
                    .append(" <gold>-</gold> <dark_gray>Phase:</dark_gray> <")
                    .append(phaseColor)
                    .append(">")
                    .append(info.getPhase().getPhase())
                    .append("</")
                    .append(phaseColor)
                    .append(">\n")
                    .append(" <gold>-</gold> <dark_gray>Container Status:</dark_gray> <red>")
                    .append(containerStatus)
                    .append("</red>\n")
                    .append(" <hover:show_text:'<dark_purple>")
                    .append(containerId)
                    .append("</dark_purple>'><gold>-</gold> <dark_gray>Container:</dark_gray> <red>")
                    .append(containerName)
                    .append("</red></hover>\n")
                    .append(" <gold>-</gold> <dark_gray>Exit Code:</dark_gray> <red>")
                    .append(exitCode)
                    .append("</red>\n")
                    .append(" <gold>-</gold> <dark_gray>Started:</dark_gray> <red>")
                    .append(startedAt)
                    .append("</red>\n")
                    .append(" <gold>-</gold> <dark_gray>Finished:</dark_gray> <red>")
                    .append(finishedAt)
                    .append("</red>\n");

            if (failureReason != null && !failureReason.isBlank()) {
                message.append(" <gold>-</gold> <dark_gray>Failure:</dark_gray> <red>")
                        .append(failureReason)
                        .append("</red>\n");
            }

            message.append("<dark_gray><b><st>－－－－－－－－－－－－－－－－－－－－</st></b></dark_gray>");

            ProtoMessage.chat().addMiniMessage(message.toString()).sendMessage(source);
        }, failure -> Log.requestError("Failed to fetch install info for " + server.getCompositeId(), failure, source));
    }

    // --- logs ---

    private static LiteralArgumentBuilder<CommandSource> logs() {
        return LiteralArgumentBuilder.<CommandSource>literal("logs")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls install logs", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(logsServer());
    }

    private static RequiredArgumentBuilder<CommandSource, String> logsServer() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("this");
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> executeLogs(
                        context.getSource(),
                        StringArgumentType.getString(context, "server"),
                        DEFAULT_PAGE,
                        DEFAULT_LINES_PER_PAGE))
                .then(logPage());
    }

    private static RequiredArgumentBuilder<CommandSource, String> logPage() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("page", StringArgumentType.string())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String pageString = StringArgumentType.getString(context, "page");
                    Integer page = parsePositiveInt(pageString);
                    if (page == null) {
                        invalidNumber(source, pageString);
                        return 0;
                    }
                    return executeLogs(source,
                            StringArgumentType.getString(context, "server"),
                            page,
                            DEFAULT_LINES_PER_PAGE);
                })
                .then(logLinesPerPage());
    }

    private static RequiredArgumentBuilder<CommandSource, String> logLinesPerPage() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("lines", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("max");
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String pageString = StringArgumentType.getString(context, "page");
                    String linesString = StringArgumentType.getString(context, "lines");
                    Integer page = parsePositiveInt(pageString);
                    if (page == null) {
                        invalidNumber(source, pageString);
                        return 0;
                    }
                    Integer lines = linesString.equals("max") ? MAX_LINES_PER_PAGE : parsePositiveInt(linesString);
                    if (lines == null) {
                        invalidNumber(source, linesString);
                        return 0;
                    }
                    return executeLogs(source,
                            StringArgumentType.getString(context, "server"),
                            page,
                            clampLinesPerPage(lines));
                });
    }

    private static int executeLogs(CommandSource source, String id, int page, int linesPerPage) {
        Server server = resolveServer(source, id);
        if (server == null) {
            if (!id.equals("this")) {
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("No such server " + id, NamedTextColor.RED)
                        .sendMessage(source);
            }
            return 0;
        }

        showInstallLogs(source, server, id, page, linesPerPage);
        return 0;
    }

    private static void showInstallLogs(CommandSource source, Server server, String id, int page, int linesPerPage) {
        PaginationAction<String> action = server.getInstallLogs(linesPerPage).skipTo(page);
        action.executeAsync(logs -> {
            int totalPages = action.getTotalPages();
            int totalLines = action.getTotal();
            int maxPage = Math.max(totalPages, 1);

            if (page > maxPage) {
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("Page " + page + " does not exist ", NamedTextColor.RED)
                        .add("(valid range: 1-" + maxPage + ")", NamedTextColor.GRAY)
                        .sendMessage(source);
                return;
            }

            ProtoMessage.chat()
                    .addMiniMessage("<dark_gray><b><st>－－</st></b><gold> Install logs for " + server.getCompositeId() + " </gold><b><st>－－</st></b></dark_gray>\n")
                    .sendMessage(source);

            ComponentBuilder<TextComponent, TextComponent.Builder> builder = Component.text();
            for (String line : logs) {
                builder.append(Component.text(line + "\n", NamedTextColor.GRAY));
            }
            source.sendMessage(builder.build());

            ProtoMessage.chat()
                    .addMiniMessage(logsPaginationFooter(id, page, linesPerPage, maxPage, totalLines))
                    .sendMessage(source);
        }, failure -> Log.requestError("Failed to get install logs for server " + id, failure, source));
    }

    // --- reinstall ---

    private static LiteralArgumentBuilder<CommandSource> reinstall() {
        return LiteralArgumentBuilder.<CommandSource>literal("reinstall")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(source);
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls install reinstall", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(reinstallServer());
    }

    private static RequiredArgumentBuilder<CommandSource, String> reinstallServer() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("this");
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String id = StringArgumentType.getString(context, "server");
                    Server server = resolveServer(source, id);
                    if (server == null) {
                        if (!id.equals("this")) {
                            ProtoMessage.chat()
                                    .add(MessagePreset.SLS)
                                    .add("No such server " + id, NamedTextColor.RED)
                                    .sendMessage(source);
                        }
                        return 0;
                    }

                    server.reinstall().executeAsync(success -> ProtoMessage.chat()
                            .add(MessagePreset.SLS)
                            .add("Reinstall started for ", NamedTextColor.GRAY)
                            .add(server.getCompositeId(), NamedTextColor.GOLD)
                            .sendMessage(source),
                            failure -> Log.requestError("Failed to reinstall server " + server.getCompositeId(), failure, source));
                    return 1;
                });
    }

    private static String logsPaginationFooter(String serverId, int page, int perPage, int totalPages, int totalLines) {
        String hover = CommandMessageParts.text("Total lines: " + totalLines + "\nLines per page: " + perPage);
        String pageText = "<hover:show_text:'" + hover + "'><gold>PAGE " + page + "/" + totalPages + "</gold></hover>";
        String previous = page > 1
                ? clickableArrow("«", "/sls install logs " + serverId + " " + (page - 1) + " " + perPage, "View newer logs")
                : "<dark_gray>«</dark_gray>";
        String next = page < totalPages
                ? clickableArrow("»", "/sls install logs " + serverId + " " + (page + 1) + " " + perPage, "View older logs")
                : "<dark_gray>»</dark_gray>";

        return "<dark_gray>" + previous + " <b><st>－－－－－－－</st></b> " + pageText + " <b><st>－－－－－－－</st></b> " + next + "</dark_gray>";
    }

    private static String clickableArrow(String arrow, String command, String hover) {
        return "<click:run_command:'" + CommandMessageParts.text(command) + "'>"
                + "<hover:show_text:'" + CommandMessageParts.text(hover) + "'>"
                + "<aqua>" + arrow + "</aqua></hover></click>";
    }

    // --- shared ---

    private static Server resolveServer(CommandSource source, String id) {
        if (id.equals("this")) {
            if (!(source instanceof Player player)) {
                Log.warn("Invalid command usage! You must specify a server id when running this command from console.");
                return null;
            }
            Server server = ServerUtils.getServer(player);
            if (server == null) {
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .add("Server " + ServerUtils.getServerName(player) + " is not an SLS server", NamedTextColor.RED)
                        .sendMessage(source);
            }
            return server;
        }
        return SLS.servers.resolve(id);
    }

    private static String phaseColor(InstallPhase phase) {
        return switch (phase) {
            case COMPLETED, IDLE -> "green";
            case INSTALLING, WARMING, POST_WARMUP -> "yellow";
            case INSTALL_FAILED, WARMUP_FAILED, POST_WARMUP_FAILED -> "red";
            default -> "gray";
        };
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "Unknown" : TimeUtils.formatTimestamp(instant);
    }

    private static String orUnknown(String value) {
        return value != null && !value.isEmpty() ? value : "Unknown";
    }

    private static Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clampLinesPerPage(int lines) {
        return Math.min(lines, MAX_LINES_PER_PAGE);
    }

    private static void invalidNumber(CommandSource source, String value) {
        ProtoMessage.chat()
                .add(MessagePreset.SLS)
                .add("Invalid number " + value, NamedTextColor.RED)
                .sendMessage(source);
    }
}
