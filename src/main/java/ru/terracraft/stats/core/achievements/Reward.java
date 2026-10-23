package ru.terracraft.stats.core.achievements;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.stats.PlayerStatsPlugin;

public record Reward(int money, String command, String permission) {

    public static final Reward NONE = new Reward(0, null, null);

    public boolean isEmpty() {
        return money <= 0
                && (command == null || command.isBlank())
                && (permission == null || permission.isBlank());
    }

    public void apply(Player player, PlayerStatsPlugin plugin) {
        if (money > 0) {
            var vault = plugin.getHooksManager() != null
                    ? plugin.getHooksManager().getVaultHook() : null;
            if (vault != null && vault.isEnabled()) {
                vault.deposit(player, money);
            } else {
                plugin.getLogger().warning("Vault не подключён: не могу выдать "
                        + money + " монет игроку " + player.getName());
            }
        }

        if (command != null && !command.isBlank()) {
            String cmd = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }
}