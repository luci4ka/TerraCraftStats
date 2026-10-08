package com.playerstats.api.java.model;

public record AchievementInfo(
        String id,
        String name,
        String description,
        String category,
        String stat,
        int threshold
) {}