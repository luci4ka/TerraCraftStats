package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.dao.LeaderboardDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.database.dao.ServerDAO;
import com.playerstats.core.database.dao.ServerStatsDAO;
import com.playerstats.core.database.dao.StatTypeDAO;
import com.playerstats.core.database.dao.TotalServerDAO;
import com.playerstats.core.services.PlayerGroupService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand {

    private final Main plugin;
    private final PlayerGroupService groupService;

    public ReloadCommand(Main plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.groupService = groupService;
    }

    /**
     * Выполнить перезагрузку плагина.
     * Перезагружает конфиг, очищает кэши DAO и сервиса групп.
     */
    public void execute(CommandSender sender) {
        if (!sender.hasPermission("playerstats.reload")) {
            sendMessage(sender, "§cУ вас нет прав для выполнения этой команды.", NamedTextColor.RED);
            return;
        }

        sendMessage(sender, "§6[PlayerStats] Начинаю перезагрузку...", NamedTextColor.GOLD);

        plugin.getThreadManager().runAsync(() -> {
            try {
                // 1. Перезагружаем конфиг
                ConfigHandler.reload(plugin);
                ConfigHandler config = ConfigHandler.getInstance(plugin);

                // 2. Очищаем кэши DAO
                clearAllCaches();

                // 3. Очищаем кэш групп
                if (groupService != null) {
                    groupService.clearCache();
                }

                // 4. Обновляем serverId в Main (на случай смены server-name в конфиге)
                String serverName = config.isMultiServerEnabled()
                        ? config.getServerName()
                        : org.bukkit.Bukkit.getServer().getName();

                plugin.getServerDAO().createOrGetServer(serverName);

                plugin.getThreadManager().runSync(() -> {
                    sendMessage(sender, "§a[PlayerStats] ✅ Конфиг перезагружен, кэши очищены!", NamedTextColor.GREEN);

                    // Информация о загруженной конфигурации
                    sender.sendMessage(Component.text()
                            .append(Component.text("  База данных: ", NamedTextColor.GRAY))
                            .append(Component.text(config.getDatabaseType().toUpperCase(), NamedTextColor.WHITE))
                            .build());

                    sender.sendMessage(Component.text()
                            .append(Component.text("  Сервер: ", NamedTextColor.GRAY))
                            .append(Component.text(serverName, NamedTextColor.WHITE))
                            .build());

                    sender.sendMessage(Component.text()
                            .append(Component.text("  API: ", NamedTextColor.GRAY))
                            .append(Component.text(config.isApiEnabled() ? "Включен" : "Выключен",
                                    config.isApiEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
                            .build());

                    if (config.isApiEnabled()) {
                        sender.sendMessage(Component.text()
                                .append(Component.text("  API Хост: ", NamedTextColor.GRAY))
                                .append(Component.text(config.getApiHost() + ":" + config.getApiPort(), NamedTextColor.WHITE))
                                .build());
                    }

                    sender.sendMessage(Component.text()
                            .append(Component.text("  Мультисерверность: ", NamedTextColor.GRAY))
                            .append(Component.text(config.isMultiServerEnabled() ? "Включена" : "Выключена",
                                    config.isMultiServerEnabled() ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                            .build());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка при перезагрузке плагина: " + e.getMessage());
                e.printStackTrace();

                plugin.getThreadManager().runSync(() -> {
                    sendMessage(sender, "§c[PlayerStats] ❌ Ошибка при перезагрузке! Проверьте консоль.", NamedTextColor.RED);
                });
            }
        });
    }

    /**
     * Очистить все кэши в DAO.
     */
    private void clearAllCaches() {
        try {
            // Очищаем кэши во всех DAO, которые их имеют
            if (plugin.getStatTypeDAO() != null) {
                plugin.getStatTypeDAO().clearCache();
            }

            if (plugin.getServerDAO() != null) {
                plugin.getServerDAO().clearCache();
            }

            // Очищаем кэш в PlayerStatsDAO через рефлексию или публичный метод
            if (plugin.getStatsDAO() != null) {
                plugin.getStatsDAO().clearCache();
            }

            if (plugin.getServerStatsDAO() != null) {
                plugin.getServerStatsDAO().clearCache();
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при очистке кэшей DAO: " + e.getMessage());
        }
    }

    /**
     * Отправить сообщение игроку или в консоль.
     */
    private void sendMessage(CommandSender sender, String message, NamedTextColor color) {
        if (sender instanceof Player) {
            sender.sendMessage(Component.text(message, color));
        } else {
            sender.sendMessage(message); // Консоль — plain text
        }
    }
}