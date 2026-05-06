package com.playerstats.core.multithreading;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ThreadManager {

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler; // Один на все отложенные задачи
    private final JavaPlugin plugin;

    /**
     * Создаёт менеджер с фиксированным пулом потоков.
     *
     * @param plugin   экземпляр плагина
     * @param poolSize количество потоков в пуле
     */
    public ThreadManager(JavaPlugin plugin, int poolSize) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Создаёт менеджер с пулом из 4 потоков (по умолчанию).
     */
    public ThreadManager(JavaPlugin plugin) {
        this(plugin, 4);
    }

    // ==================== Асинхронные задачи ====================

    /**
     * Выполнить задачу асинхронно.
     * Ошибки логируются и не крашают пул.
     */
    public void runAsync(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logError("Ошибка в асинхронной задаче", e);
            }
        });
    }

    /**
     * Выполнить задачу асинхронно с задержкой.
     * Использует общий ScheduledExecutorService (без утечек).
     */
    public void runAsyncDelayed(Runnable task, long delayMillis) {
        scheduler.schedule(() -> runAsync(task), delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Асинхронно дождаться CompletableFuture и выполнить callback на основном потоке.
     *
     * @param future   будущий результат
     * @param callback выполняется в главном потоке Bukkit при успехе
     * @param <T>      тип результата
     */
    public <T> void runAsync(CompletableFuture<T> future, Consumer<T> callback) {
        future.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                logError("Ошибка в CompletableFuture", throwable);
                return;
            }
            // Колбэк выполняем синхронно (в главном потоке)
            runSync(() -> {
                try {
                    callback.accept(result);
                } catch (Exception e) {
                    logError("Ошибка в синхронном callback", e);
                }
            });
        }, executor);
    }

    /**
     * Асинхронно дождаться CompletableFuture с раздельными callback'ами для успеха и ошибки.
     *
     * @param future    будущий результат
     * @param onSuccess выполняется в главном потоке при успехе
     * @param onError   выполняется в главном потоке при ошибке (throwable может быть null, если ошибка не связана с future)
     * @param <T>       тип результата
     */
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
                    // Не вызываем onError повторно, т.к. это ошибка колбэка, а не future
                }
            });
        }, executor);
    }

    /**
     * Выполнить CompletableFuture и обработать результат через BiConsumer (успех/ошибка) на главном потоке.
     *
     * @param future   будущий результат
     * @param callback BiConsumer<T, Throwable>: если Throwable != null — ошибка, иначе успех
     * @param <T>      тип результата
     */
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

    /**
     * Выполнить задачу в главном потоке сервера.
     * Если текущий поток уже главный — выполняется сразу, иначе через планировщик.
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                task.run();
            } catch (Exception e) {
                logError("Ошибка в синхронной задаче (прямой вызов)", e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logError("Ошибка в синхронной задаче (через планировщик)", e);
                }
            });
        }
    }

    // ==================== Жизненный цикл ====================

    /**
     * Корректное завершение всех потоков.
     * Сначала мягкое завершение с таймаутом 10 сек, затем принудительное.
     */
    public void shutdown() {
        // Завершаем scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt(); // Восстанавливаем флаг прерывания
        }

        // Завершаем основной пул
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // Восстанавливаем флаг прерывания
        }

        plugin.getLogger().info("ThreadManager завершил работу.");
    }

    // ==================== Приватные методы ====================

    /**
     * Логирование ошибки с полным стектрейсом.
     */
    private void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, message, throwable);
    }
}