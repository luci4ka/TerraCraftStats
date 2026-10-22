package ru.terracraft.stats.hooks;

import ru.terracraft.stats.PlayerStatsPlugin;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Менеджер всех внешних интеграций.
 *
 * <p>Оставлены только реально используемые хуки:</p>
 * <ul>
 *   <li>{@link VaultHook} — баланс игрока в /stats и /top</li>
 *   <li>{@link PlaceholderAPIHook} — регистрация своего expansion ({@code %ps_*%})</li>
 * </ul>
 *
 * <p>{@link DiscordWebhook} — не {@link Hook}, живёт отдельно.</p>
 */
public class HooksManager {

    private final PlayerStatsPlugin plugin;
    private final List<Hook> hooks = new ArrayList<>();

    private final VaultHook vaultHook;
    private final PlaceholderAPIHook placeholderAPIHook;

    public HooksManager(PlayerStatsPlugin plugin) {
        this.plugin = plugin;

        this.vaultHook = new VaultHook(plugin);
        this.placeholderAPIHook = new PlaceholderAPIHook(plugin);

        hooks.add(vaultHook);
        hooks.add(placeholderAPIHook);
    }

    public void init() {
        plugin.getLogger().info("Инициализация интеграций...");
        for (Hook hook : hooks) {
            try {
                hook.init();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка инициализации " + hook.getPluginName(), e);
            }
        }
    }

    public void shutdown() {
        for (Hook hook : hooks) {
            try {
                hook.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при отключении "
                        + hook.getPluginName() + ": " + e.getMessage());
            }
        }
    }

    public VaultHook getVaultHook() { return vaultHook; }
    public PlaceholderAPIHook getPlaceholderAPIHook() { return placeholderAPIHook; }

    public void logStatus() {
        Bukkit.getConsoleSender().sendMessage("§6[PlayerStats] §eСтатус интеграций:");
        for (Hook hook : hooks) {
            String status = hook.isEnabled() ? "§a✓" : "§c✗";
            Bukkit.getConsoleSender().sendMessage("  " + status + " §r" + hook.getPluginName());
        }
    }

    public List<Hook> getAll() {
        return Collections.unmodifiableList(hooks);
    }

    public int getHookCount() {
        return hooks.size();
    }
}