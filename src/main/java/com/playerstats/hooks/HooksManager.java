package com.playerstats.hooks;

import com.playerstats.Main;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Менеджер всех внешних интеграций.
 * Централизованно управляет инициализацией и завершением хуков.
 */
public class HooksManager {

    private final Main plugin;
    private final List<Hook> hooks = new ArrayList<>();

    // Хуки
    private final LuckPermsHook luckPermsHook;
    private final VaultHook vaultHook;
    private final EssentialsHook essentialsHook;
    private final PlaceholderAPIHook placeholderAPIHook;
    private final DecentHologramsHook decentHologramsHook;
    private JobsHook jobsHook;


    public HooksManager(Main plugin) {
        this.plugin = plugin;

        // Создаём хуки
        this.luckPermsHook = new LuckPermsHook(plugin);
        this.vaultHook = new VaultHook(plugin);
        this.essentialsHook = new EssentialsHook(plugin);
        this.placeholderAPIHook = new PlaceholderAPIHook(plugin);
        this.jobsHook = new JobsHook(plugin);
        this.decentHologramsHook = new DecentHologramsHook(plugin);

        // Добавляем в список
        hooks.add(luckPermsHook);
        hooks.add(vaultHook);
        hooks.add(essentialsHook);
        hooks.add(placeholderAPIHook);
        hooks.add(jobsHook);
        hooks.add(decentHologramsHook);
    }

    /**
     * Инициализировать все хуки.
     * Ошибка одного хука не ломает остальные.
     */
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

    /**
     * Завершить все хуки.
     * Ошибка одного хука не ломает остальные.
     */
    public void shutdown() {
        for (Hook hook : hooks) {
            try {
                hook.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при отключении "
                        + hook.getPluginName() + ": " + e.getMessage());
            }
        }
        hooks.clear();
    }

    // Геттеры для хуков

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public JobsHook getJobsHook() {
        return jobsHook;
    }

    public EssentialsHook getEssentialsHook() {
        return essentialsHook;
    }

    public PlaceholderAPIHook getPlaceholderAPIHook() {
        return placeholderAPIHook;
    }

    public DecentHologramsHook getDecentHologramsHook() {
        return decentHologramsHook;
    }

    /**
     * Статус всех хуков для отображения в консоли.
     * Использует ConsoleSender для цветного вывода (Logger не поддерживает §).
     */
    public void logStatus() {
        Bukkit.getConsoleSender().sendMessage("§6[PlayerStats] §eСтатус интеграций:");
        for (Hook hook : hooks) {
            String status = hook.isEnabled() ? "§a✓" : "§c✗";
            Bukkit.getConsoleSender().sendMessage("  " + status + " §r" + hook.getPluginName());
        }
    }
}