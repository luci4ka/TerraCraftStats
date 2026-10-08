package com.playerstats.api.http;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;

/**
 * Управление API-ключом: генерация, загрузка, проверка.
 */
public class ApiKeyManager {

    private static final String KEY_PREFIX = "psk_";
    private static final int KEY_BYTES = 24;  // 24 байта → 32 base64-символа
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Main plugin;
    private final ConfigHandler config;
    private volatile String apiKey;

    public ApiKeyManager(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        loadOrGenerateKey();
    }

    // ==================== LOAD / GENERATE ====================

    private void loadOrGenerateKey() {
        String configuredKey = config.getApiKey();

        if (configuredKey != null && !configuredKey.isBlank()) {
            this.apiKey = configuredKey.trim();
            plugin.getLogger().info("API key загружен из config.yml.");
            return;
        }

        if (!config.isApiAutoGenerateKey()) {
            plugin.getLogger().warning(
                    "API key не задан и auto-generate-key: false. API будет недоступен.");
            this.apiKey = null;
            return;
        }

        // Генерируем новый
        this.apiKey = generateKey();

        boolean saved = false;
        try {
            config.setApiKey(apiKey);
            config.save();
            saved = true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось сохранить API key в config.yml. "
                            + "Ключ будет работать до рестарта.", e);
        }

        if (saved) {
            plugin.getLogger().info("API key сгенерирован и сохранён в config.yml.");
        } else {
            plugin.getLogger().warning(
                    "API key сгенерирован, но НЕ сохранён. Сохраните вручную.");
        }

        if (config.isApiLogKeyOnStartup()) {
            plugin.getLogger().info("API key: " + apiKey);
            plugin.getLogger().warning(
                    "НЕ коммитьте config.yml в git — там хранится ключ!");
        }
    }

    private String generateKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return KEY_PREFIX + encoded;
    }

    // ==================== CHECK ====================

    /**
     * Проверить, авторизован ли запрос.
     */
    public boolean isAuthorized(HttpExchange exchange) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        String providedKey = extractKey(exchange);
        if (providedKey == null || providedKey.isEmpty()) {
            return false;
        }

        return constantTimeEquals(apiKey, providedKey);
    }

    /**
     * Извлечь ключ из запроса:
     * 1. X-API-Key: <key>
     * 2. Authorization: Bearer <key>
     * 3. ?api_key=<key> (fallback)
     */
    private String extractKey(HttpExchange exchange) {
        // 1. Основной заголовок
        String direct = exchange.getRequestHeaders().getFirst(config.getApiAuthHeader());
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }

        // 2. Authorization: Bearer
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null) {
            String prefix = config.getApiAuthBearerPrefix();
            if (auth.startsWith(prefix)) {
                return auth.substring(prefix.length()).trim();
            }
        }

        // 3. Query param
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                int eq = param.indexOf('=');
                if (eq > 0 && "api_key".equals(param.substring(0, eq))) {
                    return param.substring(eq + 1);
                }
            }
        }

        return null;
    }

    /**
     * Сравнение строк с константным временем.
     * Защита от timing-атак.
     */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) return false;

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    // ==================== REGENERATE ====================

    /**
     * Перегенерировать ключ (для /playerstats apikey regenerate).
     */
    public String regenerate() {
        this.apiKey = generateKey();

        try {
            config.setApiKey(apiKey);
            config.save();
            plugin.getLogger().info("API key перегенерирован и сохранён.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось сохранить новый API key. "
                            + "Ключ работает до рестарта.", e);
        }

        return apiKey;
    }

    // ==================== GETTERS ====================

    public String getApiKey() {
        return apiKey;
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isEmpty();
    }
}