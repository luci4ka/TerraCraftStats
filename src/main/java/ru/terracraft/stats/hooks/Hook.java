package ru.terracraft.stats.hooks;

/**
 * Интерфейс для всех внешних интеграций (хуков).
 */
public interface Hook {

    /**
     * Инициализация хука.
     * Вызывается при старте плагина.
     */
    void init();

    /**
     * Завершение работы хука.
     * Вызывается при выключении плагина.
     */
    void shutdown();

    /**
     * @return true, если хук успешно подключен и работает.
     */
    boolean isEnabled();

    /**
     * @return название плагина, с которым работает хук.
     */
    String getPluginName();
}