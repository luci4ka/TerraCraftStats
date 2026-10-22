package ru.terracraft.stats.api.model;

import java.util.UUID;

public record LeaderboardEntry(
        int rank,
        UUID uuid,
        String playerName,
        double value
) {}