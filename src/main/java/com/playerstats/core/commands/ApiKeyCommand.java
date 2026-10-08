package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ApiKeyCommand {

    private final Main plugin;
    private final LanguageManager lang;

    public ApiKeyCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void execute(CommandSender sender) {
        if (!sender.hasPermission("playerstats.apikey")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        var keyManager = plugin.getApiServer() != null
                ? plugin.getApiServer().getKeyManager()
                : null;

        if (keyManager == null) {
            sender.sendMessage(getComponent(sender, "apikey.api-disabled"));
            return;
        }

        if (!keyManager.hasKey()) {
            sender.sendMessage(getComponent(sender, "apikey.no-key"));
            return;
        }

        String key = keyManager.getApiKey();

        sender.sendMessage(getComponent(sender, "apikey.header"));
        sender.sendMessage(getComponent(sender, "apikey.current",
                "key", key));
        sender.sendMessage(getComponent(sender, "apikey.hint"));
    }

    public void regenerate(CommandSender sender) {
        if (!sender.hasPermission("playerstats.apikey")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        var keyManager = plugin.getApiServer() != null
                ? plugin.getApiServer().getKeyManager()
                : null;

        if (keyManager == null) {
            sender.sendMessage(getComponent(sender, "apikey.api-disabled"));
            return;
        }

        String newKey = keyManager.regenerate();

        sender.sendMessage(getComponent(sender, "apikey.regenerated"));
        sender.sendMessage(getComponent(sender, "apikey.current",
                "key", newKey));
        sender.sendMessage(getComponent(sender, "apikey.warning"));
    }

    private Component getComponent(CommandSender sender, String key, Object... repl) {
        if (sender instanceof Player player) {
            return lang.getComponent(player, key, repl);
        }
        return lang.getComponent(key, repl);
    }
}