package ru.terracraft.stats.core.config;

import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import org.bukkit.plugin.java.JavaPlugin;
import ru.terracraft.stats.core.config.model.*;

import java.nio.file.Path;
import java.util.Objects;

public class ConfigManager {

    private static volatile ConfigManager instance;

    private static final String HEADER =
            "PlayerStats configuration\n" +
                    "Документация по ключам — в комментариях к полям классов";

    private final JavaPlugin plugin;
    private final Path folder;
    private final YamlConfigurationProperties props;

    private volatile MainConfig main;
    private volatile TopChipsConfig topChips;
    private volatile TopNotificationsConfig topNotifications;
    private volatile SparklineConfig sparkline;
    private volatile DiscordConfig discord;
    private volatile CosmeticsConfig cosmetics;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath();
        this.props = YamlConfigurationProperties.newBuilder().header(HEADER).build();
        instance = this;
    }

    public static void reset() {
        instance = null;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();

        main = YamlConfigurations.update(folder.resolve("config.yml"), MainConfig.class, props);

        topChips = main.modules().topChips()
                ? YamlConfigurations.update(folder.resolve("top-chips.yml"), TopChipsConfig.class, props) : null;
        topNotifications = main.modules().topNotifications()
                ? YamlConfigurations.update(folder.resolve("top-notifications.yml"), TopNotificationsConfig.class, props) : null;
        sparkline = main.modules().sparkline()
                ? YamlConfigurations.update(folder.resolve("sparkline.yml"), SparklineConfig.class, props) : null;
        discord = main.modules().discord()
                ? YamlConfigurations.update(folder.resolve("discord.yml"), DiscordConfig.class, props) : null;
        cosmetics = main.modules().cosmetics()
                ? YamlConfigurations.update(folder.resolve("cosmetics.yml"), CosmeticsConfig.class, props) : null;

        applyDebugLevel();
        plugin.getLogger().info("Конфиги загружены (ConfigLib).");
    }

    public void reload() {
        main = YamlConfigurations.update(folder.resolve("config.yml"), MainConfig.class, props);

        topChips = main.modules().topChips()
                ? YamlConfigurations.update(folder.resolve("top-chips.yml"), TopChipsConfig.class, props) : null;
        topNotifications = main.modules().topNotifications()
                ? YamlConfigurations.update(folder.resolve("top-notifications.yml"), TopNotificationsConfig.class, props) : null;
        sparkline = main.modules().sparkline()
                ? YamlConfigurations.update(folder.resolve("sparkline.yml"), SparklineConfig.class, props) : null;
        discord = main.modules().discord()
                ? YamlConfigurations.update(folder.resolve("discord.yml"), DiscordConfig.class, props) : null;
        cosmetics = main.modules().cosmetics()
                ? YamlConfigurations.update(folder.resolve("cosmetics.yml"), CosmeticsConfig.class, props) : null;

        applyDebugLevel();
        plugin.getLogger().info("Конфиги перезагружены (ConfigLib).");
    }

    private void applyDebugLevel() {
        int level = main.debugLevel();
        if (level <= 0) plugin.getLogger().setLevel(java.util.logging.Level.WARNING);
        else if (level == 1) plugin.getLogger().setLevel(java.util.logging.Level.INFO);
        else plugin.getLogger().setLevel(java.util.logging.Level.ALL);
    }

    public MainConfig main() { return Objects.requireNonNull(main, "config not loaded"); }
    public TopChipsConfig topChips() { return topChips; }
    public TopNotificationsConfig topNotifications() { return topNotifications; }
    public SparklineConfig sparkline() { return sparkline; }
    public DiscordConfig discord() { return discord; }
    public CosmeticsConfig cosmetics() { return cosmetics; }

    public Path path() { return folder; }
}