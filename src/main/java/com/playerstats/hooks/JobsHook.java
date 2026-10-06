package com.playerstats.hooks;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.playerstats.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class JobsHook implements Hook {

    private final Main plugin;
    private boolean enabled = false;

    public JobsHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Jobs")) {
            plugin.getLogger().info("Jobs Reborn не обнаружен — интеграция отключена.");
            return;
        }

        try {
            this.enabled = true;
            plugin.getLogger().info("Jobs Reborn успешно подключен (API 5.2.6.3).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подключиться к Jobs Reborn.", e);
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getPluginName() {
        return "Jobs Reborn";
    }

    /**
     * Получить список прогрессий игрока (название профессии, уровень, опыт).
     * Возвращает пустую мапу, если игрок не состоит ни в одной профессии.
     *
     * @param player игрок
     * @return Map<название профессии, уровень>
     */
    public Map<String, Integer> getPlayerJobs(Player player) {
        Map<String, Integer> result = new HashMap<>();
        if (!isEnabled() || player == null) return result;

        try {
            JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
            if (jobsPlayer == null) return result;

            List<JobProgression> progressions = jobsPlayer.getJobProgression();
            for (JobProgression prog : progressions) {
                if (prog != null && prog.getJob() != null) {
                    result.put(prog.getJob().getName(), prog.getLevel());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка получения профессий для " + player.getName(), e);
        }
        return result;
    }

    /**
     * Получить уровень игрока в конкретной профессии.
     *
     * @param player  игрок
     * @param jobName название профессии (например, "Miner")
     * @return уровень или 0, если игрок не в профессии
     */
    public int getJobLevel(Player player, String jobName) {
        if (!isEnabled() || player == null || jobName == null) return 0;

        try {
            JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
            if (jobsPlayer == null) return 0;

            // Ищем прогрессию по имени профессии
            for (JobProgression prog : jobsPlayer.getJobProgression()) {
                if (prog != null && prog.getJob() != null
                        && prog.getJob().getName().equalsIgnoreCase(jobName)) {
                    return prog.getLevel();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка получения уровня профессии " + jobName + " для " + player.getName(), e);
        }
        return 0;
    }

    /**
     * Получить общий уровень всех профессий игрока.
     */
    public int getTotalLevel(Player player) {
        if (!isEnabled() || player == null) return 0;

        try {
            JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
            if (jobsPlayer == null) return 0;
            return jobsPlayer.getTotalLevels();
        } catch (Exception e) {
            return 0;
        }
    }
}