package com.playerstats.core.gui;

import com.playerstats.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class AchievementsGuiListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final Main plugin;

    public AchievementsGuiListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Берём тот же ключ, что в AchievementsGui.open()
        String expected = toLegacy(
                plugin.getLanguageManager().getRaw(player, "achievements.title"));

        if (!event.getView().getTitle().equals(expected)) return;

        event.setCancelled(true);

        if (event.getSlot() == 49) {
            plugin.getStatsGui().open(player);
        }
    }

    private String toLegacy(String miniMessage) {
        if (miniMessage == null) return "";
        try {
            return LEGACY.serialize(MM.deserialize(miniMessage));
        } catch (Exception e) {
            return miniMessage.replaceAll("<[^>]+>", "");
        }
    }
}