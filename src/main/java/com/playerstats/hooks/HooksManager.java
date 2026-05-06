package com.playerstats.hooks;

import com.playerstats.Main;

import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер всех внешних интеграций.
 * Централизованно управляет инициализацией и завершением хуков.
 */
public class HooksManager {

    private final Main plugin;
    private final List<Hook> hooks = new ArrayList<>();

    // Хуки
    private LuckPermsHook luckPermsHook;
    private VaultHook vaultHook;
    private EssentialsHook essentialsHook;
    private PlaceholderAPIHook placeholderAPIHook;
    private DecentHologramsHook decentHologramsHook;

    public HooksManager(Main plugin) {
        this.plugin = plugin;

        // Создаём хуки
        this.luckPermsHook = new LuckPermsHook(plugin);
        this.vaultHook = new VaultHook(plugin);
        this.essentialsHook = new EssentialsHook(plugin);
        this.placeholderAPIHook = new PlaceholderAPIHook(plugin);
        this.decentHologramsHook = new DecentHologramsHook(plugin);

        // Добавляем в список
        hooks.add(luckPermsHook);
        hooks.add(vaultHook);
        hooks.add(essentialsHook);
        hooks.add(placeholderAPIHook);
        hooks.add(decentHologramsHook);
    }

    /**
     * Инициализировать все хуки.
     */
    public void init() {
        plugin.getLogger().info("Инициализация интеграций...");
        for (Hook hook : hooks) {
            hook.init();
        }
    }

    /**
     * Завершить все хуки.
     */
    public void shutdown() {
        for (Hook hook : hooks) {
            try {
                hook.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при отключении " + hook.getPluginName() + ": " + e.getMessage());
            }
        }
    }

    // Геттеры для хуков

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
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
     */
    public void logStatus() {
        plugin.getLogger().info("Статус интеграций:");
        for (Hook hook : hooks) {
            String status = hook.isEnabled() ? "§a✓" : "§c✗";
            plugin.getLogger().info("  " + status + " §r" + hook.getPluginName());
        }
    }
}