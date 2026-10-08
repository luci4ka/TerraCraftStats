package com.playerstats.core.commands;

import com.playerstats.Main;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * /playerstats backup [now|list|restore &lt;file&gt;]
 */
public class BackupCommand {

    private final Main plugin;

    public BackupCommand(Main plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstats.backup")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        // ===== Проверка модуля =====
        if (plugin.getDatabaseBackupTask() == null) {
            sender.sendMessage(getComponent(sender, "backup.module-disabled"));
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("now")) {
            backupNow(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> list(sender);
            case "restore" -> {
                if (args.length < 2) {
                    sender.sendMessage(getComponent(sender, "backup.restore-usage"));
                    return;
                }
                restore(sender, args[1]);
            }
            default -> sender.sendMessage(getComponent(sender, "backup.usage"));
        }
    }

    // ==================== NOW ====================

    private void backupNow(CommandSender sender) {
        sender.sendMessage(getComponent(sender, "backup.starting"));

        plugin.getDatabaseBackupTask().backupAsync(ok -> {
            if (!isSenderValid(sender)) return;

            if (ok) {
                sender.sendMessage(getComponent(sender, "backup.done"));
            } else {
                sender.sendMessage(getComponent(sender, "backup.error"));
            }
        });
    }

    // ==================== LIST ====================

    private void list(CommandSender sender) {
        List<Path> backups = plugin.getDatabaseBackupTask().listBackups();

        if (backups.isEmpty()) {
            sender.sendMessage(getComponent(sender, "backup.list-empty"));
            return;
        }

        sender.sendMessage(getComponent(sender, "backup.list-header",
                "count", backups.size()));

        for (Path p : backups) {
            String name = p.getFileName().toString();
            long size;
            try {
                size = Files.size(p);
            } catch (Exception e) {
                size = 0;
            }
            sender.sendMessage(getComponent(sender, "backup.list-entry",
                    "file", name,
                    "size", humanSize(size)));
        }
    }

    // ==================== RESTORE ====================

    private void restore(CommandSender sender, String filename) {
        sender.sendMessage(getComponent(sender, "backup.restore-starting",
                "file", filename));

        // restore — в async (может быть долгим для больших БД)
        plugin.getThreadManager().runAsync(() -> {
            boolean ok = plugin.getDatabaseBackupTask().restore(filename);

            plugin.getThreadManager().runSync(() -> {
                if (!isSenderValid(sender)) return;

                if (ok) {
                    sender.sendMessage(getComponent(sender, "backup.restore-done",
                            "file", filename));
                } else {
                    sender.sendMessage(getComponent(sender, "backup.restore-error",
                            "file", filename));
                }
            });
        });
    }

    // ==================== UTIL ====================

    /**
     * Проверить, что sender ещё «валиден»:
     * если игрок — должен быть онлайн.
     */
    private boolean isSenderValid(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.isOnline();
        }
        return true;   // консоль/командный блок — всегда валидны
    }

    private Component getComponent(CommandSender sender, String key, Object... repl) {
        if (sender instanceof Player player) {
            return plugin.getLanguageManager().getComponent(player, key, repl);
        }
        return plugin.getLanguageManager().getComponent(key, repl);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB",
                bytes / 1024.0 / 1024.0);
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}