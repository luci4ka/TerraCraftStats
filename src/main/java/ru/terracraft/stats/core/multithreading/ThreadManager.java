package ru.terracraft.stats.core.multithreading;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ThreadManager {

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final JavaPlugin plugin;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public ThreadManager(JavaPlugin plugin, int poolSize) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "PlayerStats-Async");
            t.setDaemon(true);
            return t;
        });
        // 2 потока: один для «тяжёлых» cron'ов (снапшоты, PAPI), второй — для остальных
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "PlayerStats-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public ThreadManager(JavaPlugin plugin) {
        this(plugin, 4);
    }

    // ==================== Асинхронные задачи ====================

    public void runAsync(Runnable task) {
        if (shuttingDown.get()) return;
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logError("Ошибка в асинхронной задаче", e);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutdown между проверкой и submit — тихо игнорируем
        }
    }

    public void runAsyncDelayed(Runnable task, long delayMillis) {
        if (shuttingDown.get()) return;
        try {
            scheduler.schedule(() -> runAsync(task), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
        }
    }

    /**
     * Периодическая асинхронная задача.
     *
     * @param task         задача
     * @param initialDelay задержка перед первым запуском (в секундах)
     * @param period       период между запусками (в секундах)
     * @return ScheduledFuture для отмены, либо {@code null}, если менеджер уже остановлен
     */
    public ScheduledFuture<?> runAsyncRepeating(Runnable task, long initialDelay, long period) {
        if (shuttingDown.get()) return null;
        try {
            return scheduler.scheduleAtFixedRate(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logError("Ошибка в периодической async-задаче", e);
                }
            }, initialDelay, period, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            return null;
        }
    }

    public <T> void runAsync(CompletableFuture<T> future, Consumer<T> callback) {
        future.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                logError("Ошибка в CompletableFuture", throwable);
                return;
            }
            runSync(() -> {
                try {
                    callback.accept(result);
                } catch (Exception e) {
                    logError("Ошибка в синхронном callback", e);
                }
            });
        }, executor);
    }

    public <T> void runAsync(CompletableFuture<T> future, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        future.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                runSync(() -> {
                    try {
                        onError.accept(throwable);
                    } catch (Exception e) {
                        logError("Ошибка в onError callback", e);
                    }
                });
                return;
            }
            runSync(() -> {
                try {
                    onSuccess.accept(result);
                } catch (Exception e) {
                    logError("Ошибка в onSuccess callback", e);
                }
            });
        }, executor);
    }

    public <T> void runAsyncWithResult(CompletableFuture<T> future, BiConsumer<T, Throwable> callback) {
        future.whenCompleteAsync((result, throwable) -> runSync(() -> {
            try {
                callback.accept(result, throwable);
            } catch (Exception e) {
                logError("Ошибка в BiConsumer callback", e);
            }
        }), executor);
    }

    // ==================== Синхронные задачи ====================

    public void runSync(Runnable task) {
        if (shuttingDown.get()) return;

        if (Bukkit.isPrimaryThread()) {
            try {
                task.run();
            } catch (Exception e) {
                logError("Ошибка в синхронной задаче (прямой вызов)", e);
            }
        } else {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        logError("Ошибка в синхронной задаче (через планировщик)", e);
                    }
                });
            } catch (IllegalPluginAccessException ignored) {
                // плагин уже выключен
            }
        }
    }

    // ==================== Жизненный цикл ====================

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) return;

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        plugin.getLogger().info("ThreadManager завершил работу.");
    }

    private void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, message, throwable);
    }
}