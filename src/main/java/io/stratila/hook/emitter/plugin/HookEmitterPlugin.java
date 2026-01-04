package io.stratila.hook.emitter.plugin;

import java.util.Map;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import org.apache.commons.text.StringSubstitutor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.stratila.async.http.client.AsyncHttpClient;
import org.jetbrains.annotations.NotNull;

public class HookEmitterPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveResource("config.yml", false);

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendTelegramRequest(event);
    }

    private void sendTelegramRequest(PlayerEvent event) {
        String endpoint = getConfig().getString("eventHooks.telegramEndpoint");
        if (endpoint == null)
            return;

        Player player = event.getPlayer();

        String formattedString = buildFormattedJoinMessage(player, endpoint);
        String jsonPayload = buildJSONJoinPayload(player, formattedString);

        AsyncHttpClient.postJson(endpoint, jsonPayload).thenAccept(response -> {
            // Switch back to main thread if interacting with Bukkit
            Bukkit.getScheduler().runTask(this, () -> {
                getLogger().log(Level.INFO, "HookEmitter: payload " + jsonPayload + " has been sent to " + endpoint);
                player.sendMessage(Component.text("HookEmitter: Send request to a Telegram about " + player.getName()));
            });
        }).exceptionally(ex -> {
            getLogger().log(Level.SEVERE, "Failed to call endpoint: " + endpoint, ex);
            return null;
        });
    }

    private String buildFormattedJoinMessage(@NotNull Player player, String endpoint) {
        String defaultMessage = getConfig().getString("templates.defaultMessage");
        Map<String, String> values = Map.of(
                "player", player.getName(),
                "endpoint", endpoint);

        return StringSubstitutor.replace(defaultMessage, values);
    }

    private String buildJSONJoinPayload(@NotNull Player player, String joinMessage) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        ObjectNode target = mapper.createObjectNode();
        ObjectNode meta = mapper.createObjectNode();

        target.put("name", player.getName());
        target.put("uuid", player.getUniqueId().toString());
        meta.put("join_message", joinMessage);
        json.set("player", target);
        json.set("meta", meta);

        return json.toString();
    }

}
