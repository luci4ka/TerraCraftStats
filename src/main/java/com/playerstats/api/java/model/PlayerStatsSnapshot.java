package com.playerstats.api.java.model;

import java.util.Map;
import java.util.UUID;

public record PlayerStatsSnapshot(
        UUID uuid,
        String name,
        Map<String, Integer> intStats,
        Map<String, Double> doubleStats,
        Map<String, String> stringStats
) {}