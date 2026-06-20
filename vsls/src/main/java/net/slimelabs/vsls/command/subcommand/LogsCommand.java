package net.slimelabs.vsls.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import net.slimelabs.vsls.utils.message.CommandMessageParts;
import net.slimelabs.vsls.utils.message.MessageFormatter;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class LogsCommand {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_LINES_PER_PAGE = 50;
    private static final int MAX_LINES_PER_PAGE = 1000;

    public static LiteralArgumentBuilder<CommandSource> register() {
        return LiteralArgumentBuilder.<CommandSource>literal("logs")
                .requires(source -> source.hasPermission("sls.command.admin"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    ProtoMessage.chat().add(MessagePreset.INCORRECT_COMMAND_USAGE).sendMessage(context.getSource());
                    ProtoMessage.chat()
                            .add(MessageFormatter.commandUsage("/sls logs", "server"))
                            .sendMessage(source);
                    return 1;
                })
                .then(server());
    }

    private static RequiredArgumentBuilder<CommandSource, String> server() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("server", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("this");
                    SLS.servers.getShortIds().forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> execute(
                        context.getSource(),
                        StringArgumentType.getString(context, "server"),
                        DEFAULT_PAGE,
                        DEFAULT_LINES_PER_PAGE))
                .then(page());
    }

    private static RequiredArgumentBuilder<CommandSource, String> page() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("page", StringArgumentType.string())
                .executes(context -> {
                    CommandSource source = context.getSource();
                    String pageString = StringArgumentType.getString(context, "page");
                    Integer page = parsePositiveInt(pageString);
                    if (page == null) {
                        invalidNumber(source, pageString);
                        return 0;
                    }
                    return execute(
                            source,
                            StringArgumentType.getString(context, "server"),
                            page,
                            DEFAULT_LINES_PER_PAGE);
                })
                .then(linesPerPage());
    }

    private static RequiredArgumentBuilder<CommandSource, String> linesPerPage() {
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
                    Integer lines = parsePositiveInt(linesString);
                    if(linesString.equals("max")) {
                        lines = MAX_LINES_PER_PAGE;
                    }
                    if (lines == null) {
                        invalidNumber(source, linesString);
                        return 0;
                    }
                    return execute(
                            source,
                            StringArgumentType.getString(context, "server"),
                            page,
                            clampLinesPerPage(lines));
                });
    }

    private static int execute(CommandSource source, String id, int page, int linesPerPage) {
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

        displayLogs(source, server, id, page, linesPerPage);
        return 0;
    }

    private static void displayLogs(CommandSource source, Server server, String id, int page, int linesPerPage) {
        PaginationAction<String> action = server.getLogs(linesPerPage).skipTo(page);
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
                    .addMiniMessage("<dark_gray>" + "<b><st>－－</st></b>" + "<gold> Logs for " + server.getCompositeId() + " </gold>" + "<b><st>－－</st></b>" + "</dark_gray>\n")
                    .sendMessage(source);

            ComponentBuilder<TextComponent, TextComponent.Builder> builder = Component.text();
            for (String log : logs) {
                builder.append(Component.text(log + "\n", NamedTextColor.GRAY));
            }
            source.sendMessage(builder.build());

            ProtoMessage.chat()
                    .addMiniMessage(paginationFooter(id, page, linesPerPage, maxPage, totalLines))
                    .sendMessage(source);
        }, failure -> Log.requestError("Failed to get logs for server " + id, failure, source));
    }

    private static String paginationFooter(String serverId, int page, int perPage, int totalPages, int totalLines) {
        String hover = CommandMessageParts.text("Total lines: " + totalLines + "\nLines per page: " + perPage);
        String pageText = "<hover:show_text:'" + hover + "'><gold>PAGE " + page + "/" + totalPages + "</gold></hover>";
        String previous = page > 1
                ? clickableArrow("«", logsCommand(serverId, page - 1, perPage), "View newer logs")
                : "<dark_gray>«</dark_gray>";
        String next = page < totalPages
                ? clickableArrow("»", logsCommand(serverId, page + 1, perPage), "View older logs")
                : "<dark_gray>»</dark_gray>";

        return "<dark_gray>" + previous + " " + "<b><st>－－－－－－－</st></b>" + " " + pageText + " " + "<b><st>－－－－－－－</st></b>" + " " + next + "</dark_gray>";
    }

    private static String clickableArrow(String arrow, String command, String hover) {
        return "<click:run_command:'" + CommandMessageParts.text(command) + "'>"
                + "<hover:show_text:'" + CommandMessageParts.text(hover) + "'>"
                + "<aqua>" + arrow + "</aqua></hover></click>";
    }

    private static String logsCommand(String serverId, int page, int perPage) {
        return "/sls logs " + serverId + " " + page + " " + perPage;
    }

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
