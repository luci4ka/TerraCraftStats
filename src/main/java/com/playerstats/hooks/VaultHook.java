package com.playerstats.hooks;

import com.playerstats.Main;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

public class VaultHook implements Hook {

    private final Main plugin;
    private Economy economy;
    private boolean enabled = false;

    public VaultHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            plugin.getLogger().info("Vault не обнаружен — интеграция отключена.");
            return;
        }

        try {
            RegisteredServiceProvider<Economy> provider =
                    Bukkit.getServicesManager().getRegistration(Economy.class);

            if (provider != null) {
                this.economy = provider.getProvider();
                this.enabled = true;
                plugin.getLogger().info("Vault + " + economy.getName() + " успешно подключены.");
            } else {
                plugin.getLogger().warning("Vault обнаружен, но плагин экономики не найден.");
                this.enabled = false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подключиться к Vault.", e);
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        this.economy = null;
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled && economy != null;
    }

    @Override
    public String getPluginName() {
        return "Vault";
    }

    /**
     * Получить API экономики.
     */
    public Economy getEconomy() {
        return economy;
    }

    /**
     * Получить баланс игрока.
     */
    public double getBalance(Player player) {
        if (!isEnabled()) return 0.0;
        return economy.getBalance(player);
    }

    /**
     * Получить баланс офлайн-игрока.
     */
    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) return 0.0;
        return economy.getBalance(player);
    }

    /**
     * Проверить, есть ли у игрока достаточно денег.
     */
    public boolean has(Player player, double amount) {
        if (!isEnabled()) return false;
        return economy.has(player, amount);
    }

    /**
     * Форматировать сумму в соответствии с плагином экономики.
     */
    public String format(double amount) {
        if (!isEnabled()) return String.format("%.2f", amount);
        return economy.format(amount);
    }
}