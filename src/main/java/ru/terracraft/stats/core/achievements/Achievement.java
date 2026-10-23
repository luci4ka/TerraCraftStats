package ru.terracraft.stats.core.achievements;

public record Achievement(
        String id,
        String statKey,
        long threshold,
        String displayName,
        String message,
        String goal,
        Reward reward
) {}