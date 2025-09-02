package com.playerstats.core.multithreading;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class ThreadManager {

    private final ExecutorService executor;
    private final JavaPlugin plugin;

    public ThreadManager(JavaPlugin plugin, int poolSize) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(poolSize);
    }

    public ThreadManager(JavaPlugin plugin) {
        this(plugin, 4); // По умолчанию 4 потока
    }

    /** Асинхронное выполнение задачи */
    public void runAsync(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка в асинхронной задаче: " + e);
                e.printStackTrace();
            }
        });
    }

    /** Асинхронное выполнение CompletableFuture с безопасным callback */
    public <T> void runAsync(CompletableFuture<T> future, Consumer<T> callback) {
        future.thenAcceptAsync(result -> runSync(() -> {
            try {
                callback.accept(result);
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка в синхронном callback: " + e);
                e.printStackTrace();
            }
        }), executor).exceptionally(ex -> {
            plugin.getLogger().severe("Ошибка в CompletableFuture: " + ex);
            ex.printStackTrace();
            return null;
        });
    }

    /** Синхронное выполнение задачи на основном потоке */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка в синхронной задаче: " + e);
                e.printStackTrace();
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка в синхронной задаче: " + e);
                    e.printStackTrace();
                }
            });
        }
    }

    /** Корректное завершение пула потоков */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    /** Асинхронная задача с задержкой (в миллисекундах) */
    public void runAsyncDelayed(Runnable task, long delayMillis) {
        Executors.newSingleThreadScheduledExecutor().schedule(() -> runAsync(task), delayMillis, TimeUnit.MILLISECONDS);
    }

}
