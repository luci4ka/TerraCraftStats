package com.playerstats.core.achievements;

import java.util.List;

/**
 * Достижение.
 *
 * @param id          уникальный ID
 * @param name        отображаемое имя
 * @param description описание
 * @param icon        Material для GUI
 * @param stat        статистика, которая проверяется (например, "kills")
 * @param threshold   порог (например, 100)
 * @param commands    команды для выполнения (от консоли)
 * @param broadcast   сообщать ли в чат
 * @param category    категория ("pvp", "pve", "blocks", ...)
 */
public record Achievement(
        String id,
        String name,
        String description,
        String icon,
        String stat,
        int threshold,
        List<String> commands,
        boolean broadcast,
        String category
) {}