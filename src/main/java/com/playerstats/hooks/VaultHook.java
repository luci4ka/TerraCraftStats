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

    public Economy getEconomy() {
        return economy;
    }

    /**
     * Получить баланс игрока.
     * Пробуем через OfflinePlayer, чтобы обойти проблему с Vault 2.5.0
     */
    public double getBalance(Player player) {
        if (!isEnabled() || player == null) return 0.0;

        // Player является OfflinePlayer, передаём его во второй метод
        return getBalance((OfflinePlayer) player);
    }

    /**
     * Получить баланс офлайн-игрока.
     * Этот метод напрямую вызывает economy.getBalance(OfflinePlayer)
     */
    public double getBalance(OfflinePlayer player) {
        if (!isEnabled() || player == null) return 0.0;
        try {
            return economy.getBalance(player);
        } catch (Throwable e) {
            plugin.getLogger().warning(
                    "Vault: ошибка баланса для " + player.getName()
                            + " (" + e.getClass().getSimpleName() + ")");
            return 0.0;
        }
    }

    /**
     * Проверить, есть ли у игрока достаточно денег.
     */
    public boolean has(Player player, double amount) {
        if (!isEnabled() || player == null) return false;
        try {
            return economy.has(player, amount);
        } catch (Throwable e) {
            plugin.getLogger().warning(
                    "Vault: ошибка проверки баланса для " + player.getName()
                            + " (" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }

    /**
     * Форматировать сумму в соответствии с плагином экономики.
     */
    public String format(double amount) {
        if (!isEnabled()) return String.format("%.2f", amount);
        try {
            return economy.format(amount);
        } catch (Throwable e) {
            return String.format("%.2f", amount);
        }
    }
}