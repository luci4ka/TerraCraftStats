package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import org.bukkit.command.CommandSender;

public class HelpCommand {

    private final Main plugin;
    private final LanguageManager lang;

    public HelpCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void execute(CommandSender sender) {
        sender.sendMessage(lang.getComponent("help.header"));
        sender.sendMessage(lang.getComponent("help.title"));
        sender.sendMessage(lang.getComponent("help.commands-title"));

        boolean hasAnyCommand = false;

        // /stats — своя статистика
        if (sender.hasPermission("playerstats.stats")) {
            sender.sendMessage(lang.getComponent("help.command-stats"));
            hasAnyCommand = true;
        }

        // /stats <player> — чужая статистика
        if (sender.hasPermission("playerstats.stats.others")) {
            sender.sendMessage(lang.getComponent("help.command-stats-other"));
            hasAnyCommand = true;
        }

        // /stats gui — GUI
        if (sender.hasPermission("playerstats.stats")) {
            sender.sendMessage(lang.getComponent("help.command-stats-gui"));
            hasAnyCommand = true;
        }

        // /top
        if (sender.hasPermission("playerstats.top")) {
            sender.sendMessage(lang.getComponent("help.command-top"));
            hasAnyCommand = true;
        }

        // /share
        if (sender.hasPermission("playerstats.share")) {
            sender.sendMessage(lang.getComponent("help.command-share"));
            hasAnyCommand = true;
        }

        // /compare
        if (sender.hasPermission("playerstats.compare")) {
            sender.sendMessage(lang.getComponent("help.command-compare"));
            hasAnyCommand = true;
        }

        // /exclude
        if (sender.hasPermission("playerstats.exclude")) {
            sender.sendMessage(lang.getComponent("help.command-exclude"));
            hasAnyCommand = true;
        }

        // /playerstats reload
        if (sender.hasPermission("playerstats.reload")) {
            sender.sendMessage(lang.getComponent("help.command-reload"));
            hasAnyCommand = true;
        }

        // /playerstats info
        if (sender.hasPermission("playerstats.info")) {
            sender.sendMessage(lang.getComponent("help.command-info"));
            hasAnyCommand = true;
        }

        // /playerstats update
        if (sender.hasPermission("playerstats.update")) {
            sender.sendMessage(lang.getComponent("help.command-update"));
            hasAnyCommand = true;
        }

        if (!hasAnyCommand) {
            sender.sendMessage(lang.getComponent("help.no-commands"));
        }

        sender.sendMessage(lang.getComponent("help.footer"));
    }
}