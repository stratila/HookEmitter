package io.stratila.hook.emitter.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.stratila.async.http.client.AsyncHttpClient;
import java.util.Map;
import java.util.UUID;
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
import org.jetbrains.annotations.NotNull;

public class HookEmitterPlugin extends JavaPlugin implements Listener {
  @Override
  public void onEnable() {
    Bukkit.getPluginManager().registerEvents(this, this);
    this.getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            commands -> {
              LiteralCommandNode<CommandSourceStack> joinMsgCommand =
                  createSetJoinMsgCommand("join_msg");
              commands.registrar().register(joinMsgCommand);
            });
    saveResource("config.yml", false);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    sendTelegramRequest(event);
  }

  private void sendTelegramRequest(PlayerEvent event) {
    String endpoint = getConfig().getString("eventHooks.telegramEndpoint");
    if (endpoint == null) return;

    Player player = event.getPlayer();

    String formattedString = buildFormattedJoinMessage(player, endpoint);
    String jsonPayload = buildJSONJoinPayload(player, formattedString);

    AsyncHttpClient.postJson(endpoint, jsonPayload)
        .thenAccept(
            response -> {
              // Switch back to main thread if interacting with Bukkit
              Bukkit.getScheduler()
                  .runTask(
                      this,
                      () -> {
                        getLogger()
                            .log(
                                Level.INFO,
                                "Payload " + jsonPayload + " has been sent to " + endpoint);
                        player.sendMessage(
                            Component.text(
                                "HookEmitter: Send request to a Telegram about "
                                    + player.getName()));
                      });
            })
        .exceptionally(
            ex -> {
              getLogger().log(Level.SEVERE, "Failed to call endpoint: " + endpoint, ex);
              return null;
            });
  }

  private String buildFormattedJoinMessage(@NotNull Player player, String endpoint) {
    String defaultMessage = getConfig().getString("templates.defaultMessage");
    Map<String, String> values = Map.of("player", player.getName(), "endpoint", endpoint);

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

  public LiteralCommandNode<CommandSourceStack> createSetJoinMsgCommand(final String commandName) {
    return Commands.literal(commandName)
        .then(
            Commands.argument("target", ArgumentTypes.player())
                .then(
                    Commands.argument("message", StringArgumentType.greedyString())
                        .executes(
                            ctx -> {
                              final PlayerSelectorArgumentResolver playerSelector =
                                  ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                              final Player targetPlayer =
                                  playerSelector.resolve(ctx.getSource()).getFirst();

                              UUID targetPlayerUuid = targetPlayer.getUniqueId();
                              String message = StringArgumentType.getString(ctx, "message");

                              getConfig().set("playerMessages." + targetPlayerUuid, message);
                              saveConfig();
                              getLogger()
                                  .log(
                                      Level.INFO,
                                      "Set a custom message for "
                                          + targetPlayer.getName()
                                          + "("
                                          + targetPlayerUuid
                                          + "): "
                                          + message);

                              return Command.SINGLE_SUCCESS;
                            })))
        .build();
  }
}
