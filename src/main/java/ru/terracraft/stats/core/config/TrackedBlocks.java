package ru.terracraft.stats.core.config;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Список ценных блоков, для которых ведётся отдельная статистика.
 * Наполняется из {@code MainConfig.tracking().blocks()} через ConfigManager.
 */
public final class TrackedBlocks {

    private final Set<String> blocks;

    public TrackedBlocks(List<String> fromConfig) {
        if (fromConfig == null || fromConfig.isEmpty()) {
            this.blocks = Set.of();
            return;
        }

        Set<String> parsed = new HashSet<>();
        for (String name : fromConfig) {
            if (name == null || name.isEmpty()) continue;

            String lower = name.toLowerCase().trim();

            try {
                Material material = Material.valueOf(lower.toUpperCase());
                if (material.isBlock() && !material.isAir()) {
                    parsed.add(lower);
                }
            } catch (IllegalArgumentException ignored) {
                // Материал не существует — пропускаем
            }
        }

        this.blocks = Collections.unmodifiableSet(parsed);
    }

    public boolean isTracked(Material material) {
        return blocks.contains(material.name().toLowerCase());
    }

    public boolean isTracked(String name) {
        return blocks.contains(name.toLowerCase());
    }

    public Set<String> getBlocks() {
        return blocks;
    }

    public List<String> toStatTypes() {
        return blocks.stream()
                .flatMap(name -> java.util.stream.Stream.of(
                        "block_placed_" + name,
                        "block_broken_" + name
                ))
                .toList();
    }
}