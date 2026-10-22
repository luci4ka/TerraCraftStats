package ru.terracraft.stats.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import ru.terracraft.api.TerraCoreAPI;
import ru.terracraft.stats.PlayerStatsPlugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Тонкая обёртка над {@link TerraCoreAPI}.
 *
 * <p>Если TerraCraftCore не установлен или API не зарегистрирован —
 * все методы возвращают {@code false}, статистика не блокируется.</p>
 *
 * <p><b>Важно:</b> в {@code paper-plugin.yml} требуется
 * {@code join-classpath: true} для TerraCraftCore, иначе
 * {@code NoClassDefFoundError} в рантайме.</p>
 */
public class TerraCoreBridge {

    private final PlayerStatsPlugin plugin;
    private volatile TerraCoreAPI api;

    public TerraCoreBridge(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("TerraCraftCore")) {
                plugin.getLogger().info("TerraCraftCore не обнаружен — AFK/god-проверки отключены.");
                this.api = null;
                return;
            }

            this.api = Bukkit.getServicesManager().load(TerraCoreAPI.class);

            if (api == null) {
                plugin.getLogger().warning(
                        "TerraCraftCore установлен, но TerraCoreAPI не зарегистрирован. "
                                + "Обновите TerraCraftCore.");
            } else {
                plugin.getLogger().info("TerraCraftCore API подключён.");
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка подключения к TerraCraftCore API", t);
            this.api = null;
        }
    }

    public boolean isEnabled() {
        return api != null;
    }

    @Nullable
    public TerraCoreAPI api() {
        return api;
    }

    // ==================== Проверки по UUID ====================

    public boolean isAfk(UUID uuid) {
        if (api == null || uuid == null) return false;
        try { return api.isAfk(uuid); }
        catch (Throwable t) { return false; }
    }

    public boolean isGod(UUID uuid) {
        if (api == null || uuid == null) return false;
        try { return api.isGod(uuid); }
        catch (Throwable t) { return false; }
    }

    public boolean isVanished(UUID uuid) {
        if (api == null || uuid == null) return false;
        try { return api.isVanished(uuid); }
        catch (Throwable t) { return false; }
    }

    // ==================== Удобные обёртки для Player ====================
    // (в самом TerraCoreAPI перегрузок с Player нет — только с UUID)

    public boolean isAfk(Player player) {
        return player != null && isAfk(player.getUniqueId());
    }

    public boolean isGod(Player player) {
        return player != null && isGod(player.getUniqueId());
    }

    public boolean isVanished(Player player) {
        return player != null && isVanished(player.getUniqueId());
    }
}