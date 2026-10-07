package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HelpCommand {

    private final Main plugin;
    private final LanguageManager lang;

    public HelpCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void execute(CommandSender sender) {
        int availableCommands = countAvailableCommands(sender);

        // ==================== ЗАГОЛОВОК ====================
        sender.sendMessage(getComponent(sender, "help.header"));
        sender.sendMessage(getComponent(sender, "help.title"));
        sender.sendMessage(getComponent(sender, "help.available",
                "count", availableCommands));

        if (availableCommands == 0) {
            sender.sendMessage(getComponent(sender, "help.header"));
            sender.sendMessage(getComponent(sender, "help.no-commands"));
            sender.sendMessage(getComponent(sender, "help.header"));
            return;
        }

        // ==================== СТАТИСТИКА ====================
        boolean hasStats = sender.hasPermission("playerstats.stats");
        boolean hasStatsOthers = sender.hasPermission("playerstats.stats.others");

        if (hasStats || hasStatsOthers) {
            sender.sendMessage(getComponent(sender, "help.section-stats"));

            if (hasStats) {
                sendCommand(sender, "help.command-stats",
                        "/stats", "help.hover-stats");
                sendCommand(sender, "help.command-stats-gui",
                        "/stats gui", "help.hover-stats-gui");
            }

            if (hasStatsOthers) {
                sendCommand(sender, "help.command-stats-other",
                        "/stats ", "help.hover-stats-other");
            }

            if (hasStats || hasStatsOthers) {
                sendCommand(sender, "help.command-stats-pvp",
                        "/stats pvp ", "help.hover-stats-pvp");
            }
        }

        // ==================== ТОПЫ ====================
        if (sender.hasPermission("playerstats.top")) {
            sender.sendMessage(getComponent(sender, "help.section-tops"));

            sendCommand(sender, "help.command-top",
                    "/top", "help.hover-top");
            sendCommand(sender, "help.command-top-stat",
                    "/top ", "help.hover-top-stat");
            sendCommand(sender, "help.command-top-pvp",
                    "/top pvp ", "help.hover-top-pvp");
        }

        // ==================== СОЦИАЛЬНОЕ ====================
        boolean hasShare = sender.hasPermission("playerstats.share");
        boolean hasCompare = sender.hasPermission("playerstats.compare");

        if (hasShare || hasCompare) {
            sender.sendMessage(getComponent(sender, "help.section-social"));

            if (hasShare) {
                sendCommand(sender, "help.command-share",
                        "/share ", "help.hover-share");
            }

            if (hasCompare) {
                sendCommand(sender, "help.command-compare",
                        "/compare ", "help.hover-compare");
            }
        }

        // ==================== УПРАВЛЕНИЕ ====================
        boolean hasExclude = sender.hasPermission("playerstats.exclude");
        boolean hasReload = sender.hasPermission("playerstats.reload");
        boolean hasInfo = sender.hasPermission("playerstats.info");
        boolean hasUpdate = sender.hasPermission("playerstats.update");

        if (hasExclude || hasReload || hasInfo || hasUpdate) {
            sender.sendMessage(getComponent(sender, "help.section-admin"));

            if (hasExclude) {
                sendCommand(sender, "help.command-exclude",
                        "/exclude ", "help.hover-exclude");
            }

            if (hasReload) {
                sendCommand(sender, "help.command-reload",
                        "/playerstats reload", "help.hover-reload");
            }

            if (hasInfo) {
                sendCommand(sender, "help.command-info",
                        "/playerstats info", "help.hover-info");
            }

            if (hasUpdate) {
                sendCommand(sender, "help.command-update",
                        "/playerstats update", "help.hover-update");
            }
        }

        // ==================== ПОДВАЛ ====================
        sender.sendMessage(getComponent(sender, "help.header"));
    }

    // ==================== ВНУТРЕННЕЕ ====================

    /**
     * Отправить строку команды с hover и кликом.
     *
     * @param sender        получатель
     * @param langKey       ключ команды в lang.yml
     * @param command       команда (для click — вставить в чат)
     * @param hoverLangKey  ключ hover в lang.yml
     */
    private void sendCommand(CommandSender sender, String langKey,
                             String command, String hoverLangKey) {
        Component line = getComponent(sender, langKey);

        // Hover — на языке отправителя
        Component hoverText = getComponent(sender, hoverLangKey);
        line = line.hoverEvent(HoverEvent.showText(hoverText));

        // Клик — подставить в чат
        line = line.clickEvent(ClickEvent.suggestCommand(command));

        sender.sendMessage(line);
    }

    /**
     * Подсчитать количество доступных команд.
     */
    private int countAvailableCommands(CommandSender sender) {
        int count = 0;

        if (sender.hasPermission("playerstats.stats")) count += 3;
        if (sender.hasPermission("playerstats.stats.others")) count += 2;
        if (sender.hasPermission("playerstats.top")) count += 3;
        if (sender.hasPermission("playerstats.share")) count += 1;
        if (sender.hasPermission("playerstats.compare")) count += 1;
        if (sender.hasPermission("playerstats.exclude")) count += 1;
        if (sender.hasPermission("playerstats.reload")) count += 1;
        if (sender.hasPermission("playerstats.info")) count += 1;
        if (sender.hasPermission("playerstats.update")) count += 1;

        return count;
    }

    // ==================== LANG HELPERS ====================

    private Component getComponent(CommandSender sender, String key, Object... replacements) {
        if (sender instanceof Player player) {
            return lang.getComponent(player, key, replacements);
        }
        return lang.getComponent(key, replacements);
    }
}