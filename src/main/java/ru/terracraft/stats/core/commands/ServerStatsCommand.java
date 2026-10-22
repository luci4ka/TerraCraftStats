package ru.terracraft.stats.core.commands;

import dev.jorel.commandapi.CommandAPICommand;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.database.dao.TotalServerDAO;
import ru.terracraft.stats.core.utils.FormatUtil;
import ru.terracraft.stats.core.utils.SpriteUtil;
import ru.terracraft.stats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;

public class ServerStatsCommand {

    /** Спрайты для строк /serverstats. */
    private static final Map<String, Component> STAT_SPRITES = Map.of(
            "serverstats.unique-players", SpriteUtil.item("minecraft:item/player_head", "👤"),
            "serverstats.line-time",      SpriteUtil.item("minecraft:item/clock", "⏳"),
            "serverstats.line-balance",   SpriteUtil.item("minecraft:item/gold_ingot", "💰")
    );

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;

    public ServerStatsCommand(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void register() {
        new CommandAPICommand("serverstats")
                .withPermission("playerstats.serverstats")
                .executes((sender, args) -> {
                    execute(sender);
                    return 0;
                })
                .register();
    }

    public void execute(CommandSender sender) {
        if (!sender.hasPermission("playerstats.serverstats")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                TotalServerDAO dao = plugin.getTotalServerDAO();
                Map<String, Number> stats = dao.getServerStats(serverId);
                plugin.getThreadManager().runSync(() -> sendServerStats(sender, stats));
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка статистики сервера: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "error")));
            }
        });
    }

    private void sendServerStats(CommandSender sender, Map<String, Number> stats) {
        Component separator = getComponent(sender, "stats.header");

        String serverDisplay = plugin.getServerName();

        sender.sendMessage(separator);
        sender.sendMessage(getComponent(sender, "serverstats.title",
                "server", serverDisplay));
        sender.sendMessage(separator);

        sender.sendMessage(getComponent(sender, "serverstats.section-players"));

        Number uniquePlayers = stats.getOrDefault("uniquePlayers", 0);
        sendStatLine(sender, "serverstats.unique-players",
                FormatUtil.formatInteger(uniquePlayers.longValue()),
                null, null);

        sender.sendMessage(getComponent(sender, "serverstats.section-activity"));

        sendStat(sender, stats, "play_time",
                "play_time", "serverstats.stat-playtime-hover");
        sendStat(sender, stats, "mobs_killed",
                "mobs_killed", "serverstats.stat-mobs-hover");
        sendStat(sender, stats, "bosses_killed",
                "bosses_killed", "serverstats.stat-bosses-hover");
        sendStat(sender, stats, "kills",
                "kills", "serverstats.stat-kills-hover");
        sendStat(sender, stats, "deaths",
                "deaths", "serverstats.stat-deaths-hover");

        sender.sendMessage(getComponent(sender, "serverstats.section-blocks"));

        sendStat(sender, stats, "blocks_placed",
                "blocks_placed", "serverstats.stat-blocks-placed-hover");
        sendStat(sender, stats, "blocks_broken",
                "blocks_broken", "serverstats.stat-blocks-broken-hover");

        sender.sendMessage(getComponent(sender, "serverstats.section-economy"));

        Number balance = stats.getOrDefault("balance", 0);
        sendStatLine(sender, "serverstats.line-balance",
                FormatUtil.formatBalance(lang, balance.doubleValue()),
                "serverstats.stat-balance-hover",
                "balance");

        sender.sendMessage(separator);
    }

    private void sendStat(CommandSender sender, Map<String, Number> stats,
                          String key, String statKey, String hoverLangKey) {
        Number value = stats.get(key);
        if (value == null) return;

        String formatted;
        if (key.equals("play_time")) {
            formatted = TimeUtil.formatTime(lang, sender, value.intValue());
        } else if (key.equals("balance")) {
            formatted = FormatUtil.formatBalance(lang, value.doubleValue());
        } else {
            formatted = FormatUtil.formatInteger(value.longValue());
        }

        sendStatLine(sender, null, formatted, hoverLangKey, statKey);
    }

    private void sendStatLine(CommandSender sender, String langKey,
                              String formatted,
                              String hoverLangKey, String statKey) {
        Component line;

        if (langKey != null) {
            line = getComponent(sender, langKey, "value", formatted);
            Component sprite = STAT_SPRITES.get(langKey);
            if (sprite != null) line = sprite.append(Component.text(" ")).append(line);
        } else {
            line = getComponent(sender, "serverstats.line", "value", formatted);
        }

        if (hoverLangKey != null) {
            Component hoverComponent = getComponent(sender, hoverLangKey);

            if (statKey != null) {
                hoverComponent = hoverComponent
                        .append(Component.newline())
                        .append(getComponent(sender, "serverstats.click-hint",
                                "stat", statKey));
            }

            line = line.hoverEvent(HoverEvent.showText(hoverComponent));
        }

        if (statKey != null) {
            line = line.clickEvent(ClickEvent.runCommand("/top " + statKey));
        }

        sender.sendMessage(line);
    }

    private Component getComponent(CommandSender sender, String key, Object... replacements) {
        if (sender instanceof Player player) {
            return lang.getComponent(player, key, replacements);
        }
        return lang.getComponent(key, replacements);
    }
}