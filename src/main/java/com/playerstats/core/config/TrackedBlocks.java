package com.playerstats.core.config;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Список ценных блоков, для которых ведётся отдельная статистика.
 * Читается из config.yml (секция tracked-blocks).
 */
public final class TrackedBlocks {

    /**
     * Блоки по умолчанию (если в конфиге ничего не указано).
     */
    private static final Set<String> DEFAULT_BLOCKS = Set.of(
            "diamond_ore", "deepslate_diamond_ore",
            "ancient_debris",
            "emerald_ore", "deepslate_emerald_ore",
            "gold_ore", "deepslate_gold_ore", "nether_gold_ore",
            "iron_ore", "deepslate_iron_ore",
            "coal_ore", "deepslate_coal_ore",
            "lapis_ore", "deepslate_lapis_ore",
            "redstone_ore", "deepslate_redstone_ore",
            "copper_ore", "deepslate_copper_ore",
            "netherite_block", "diamond_block", "emerald_block",
            "gold_block", "iron_block", "lapis_block",
            "redstone_block", "coal_block", "copper_block"
    );

    private final Set<String> blocks;

    public TrackedBlocks(List<String> fromConfig) {
        if (fromConfig == null || fromConfig.isEmpty()) {
            this.blocks = DEFAULT_BLOCKS;
            return;
        }

        Set<String> parsed = new HashSet<>();
        for (String name : fromConfig) {
            if (name == null || name.isEmpty()) continue;

            String lower = name.toLowerCase().trim();

            // Проверяем, что такой Material существует
            try {
                Material material = Material.valueOf(lower.toUpperCase());
                if (material.isBlock() && !material.isAir()) {
                    parsed.add(lower);
                }
            } catch (IllegalArgumentException e) {
                // Материал не существует — пропускаем
            }
        }

        this.blocks = parsed.isEmpty() ? DEFAULT_BLOCKS : Collections.unmodifiableSet(parsed);
    }

    /**
     * Проверить, отслеживается ли блок.
     */
    public boolean isTracked(Material material) {
        return blocks.contains(material.name().toLowerCase());
    }

    /**
     * Проверить, отслеживается ли блок по имени.
     */
    public boolean isTracked(String name) {
        return blocks.contains(name.toLowerCase());
    }

    /**
     * Получить все отслеживаемые блоки.
     */
    public Set<String> getBlocks() {
        return blocks;
    }

    /**
     * Получить список типов статистики для отслеживаемых блоков.
     */
    public List<String> toStatTypes() {
        return blocks.stream()
                .flatMap(name -> java.util.stream.Stream.of(
                        "block_placed_" + name,
                        "block_broken_" + name
                ))
                .toList();
    }
}