package ru.terracraft.stats.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Публичный вход в {@link PlayerStatsApi}.
 *
 * <p>Тонкий фасад над Bukkit {@code ServicesManager}. Используйте его, если
 * не хотите помнить про {@code getRegistration(...)} и {@code ServicePriority}.</p>
 *
 * <pre>{@code
 * PlayerStatsApi api = PlayerStatsApiProvider.get();
 * if (api == null) {
 *     getLogger().warning("PlayerStats не установлен");
 *     return;
 * }
 * int kills = api.getStat(player, "kills");
 * }</pre>
 *
 * <p><b>Важно:</b> подключение — только {@code compileOnly}, не shade'ить.
 * См. {@code README-API.md}.</p>
 */
public final class PlayerStatsApiProvider {

    private PlayerStatsApiProvider() {}

    /**
     * @return экземпляр API или {@code null}, если PlayerStats не установлен / не включён
     */
    @Nullable
    public static PlayerStatsApi get() {
        RegisteredServiceProvider<PlayerStatsApi> provider =
                Bukkit.getServicesManager().getRegistration(PlayerStatsApi.class);
        return provider != null ? provider.getProvider() : null;
    }

    /**
     * @return {@code true}, если PlayerStats установлен и API доступен
     */
    public static boolean isAvailable() {
        return get() != null;
    }

    /**
     * Как {@link #get()}, но бросает {@link IllegalStateException}, если API недоступен.
     * Удобно, если PlayerStats — обязательная зависимость ({@code depend: [PlayerStats]}).
     */
    @NotNull
    public static PlayerStatsApi getRequired() {
        PlayerStatsApi api = get();
        if (api == null) {
            throw new IllegalStateException(
                    "PlayerStats API недоступен: плагин не установлен или не включён. "
                            + "Проверьте depend/softdepend в plugin.yml.");
        }
        return api;
    }
}