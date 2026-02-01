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
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.text.StringSubstitutor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
              LiteralCommandNode<CommandSourceStack> setJoinMsgCommand =
                  createSetJoinMsgCommand("set_join_msg");
              LiteralCommandNode<CommandSourceStack> resetJoinMsgCommand =
                  createResetJoinMsgCommand("reset_join_msg");
              commands.registrar().register(setJoinMsgCommand);
              commands.registrar().register(resetJoinMsgCommand);
            });
    saveResource("config.yml", false);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    sendJoinRequest(event);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    sendQuitRequest(event);
  }

  private void sendJoinRequest(PlayerEvent event) {
    String endpoint = getConfig().getString("eventHooks.joinEndpoint");
    if (endpoint == null) return;

    Player player = event.getPlayer();

    String formattedString = buildFormattedJoinMessage(player, endpoint);
    String jsonPayload = buildJSONPayload(player, formattedString);

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
                      });
            })
        .exceptionally(
            ex -> {
              getLogger().log(Level.SEVERE, "Failed to call endpoint: " + endpoint, ex);
              return null;
            });
  }

  private void sendQuitRequest(PlayerEvent event) {
    String endpoint = getConfig().getString("eventHooks.quitEndpoint");
    if (endpoint == null) return;

    Player player = event.getPlayer();

    String jsonPayload = buildJSONPayload(player, null);

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
                      });
            })
        .exceptionally(
            ex -> {
              getLogger().log(Level.SEVERE, "Failed to call endpoint: " + endpoint, ex);
              return null;
            });
  }

  private String buildFormattedJoinMessage(@NotNull Player player, String endpoint) {

    String message = getConfig().getString("templates.defaultMessage");
    String customMessage = getConfig().getString("playerMessages." + player.getUniqueId());
    if (customMessage != null) message = customMessage;

    Map<String, String> values = Map.of("player", player.getName(), "endpoint", endpoint);

    return StringSubstitutor.replace(message, values);
  }

  private String buildJSONPayload(@NotNull Player player, String joinMessage) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode json = mapper.createObjectNode();
    ObjectNode target = mapper.createObjectNode();
    ObjectNode meta = mapper.createObjectNode();

    target.put("name", player.getName());
    target.put("uuid", player.getUniqueId().toString());
    meta.put("message", joinMessage);
    json.set("player", target);
    json.set("meta", meta);

    return json.toString();
  }

  public LiteralCommandNode<CommandSourceStack> createResetJoinMsgCommand(
      final String commandName) {
    return Commands.literal(commandName)
        .then(
            Commands.argument("target", ArgumentTypes.player())
                .executes(
                    ctx -> {
                      final PlayerSelectorArgumentResolver playerSelector =
                          ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
                      final Player targetPlayer =
                          playerSelector.resolve(ctx.getSource()).getFirst();

                      final CommandSender sender =
                          ctx.getSource().getSender(); // Retrieve the command sender
                      final Entity executor =
                          ctx.getSource()
                              .getExecutor(); // Retrieve the command executor, which may or
                      // may not be the same as the sender

                      // Check whether the executor is a player, as you can only set a
                      // player's flight speed
                      if (!(executor instanceof Player player)) {
                        // If a non-player tried to set their own flight speed
                        sender.sendPlainMessage("Not a player");
                        return Command.SINGLE_SUCCESS;
                      }

                      boolean editSelf = targetPlayer.getUniqueId().equals(executor.getUniqueId());
                      boolean hasEditOthersPerm =
                          sender.hasPermission("hookemitter" + ".join_msg.others");

                      if (!editSelf && !hasEditOthersPerm) {
                        sender.sendMessage(
                            Component.text(
                                    "You don't have permission to reset others' join messages.")
                                .color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                      }

                      UUID targetPlayerUuid = targetPlayer.getUniqueId();

                      getConfig().set("playerMessages." + targetPlayerUuid, null);
                      saveConfig();

                      getLogger()
                          .log(
                              Level.INFO,
                              "HookEmitter: Reset custom message, using default one since now");

                      return Command.SINGLE_SUCCESS;
                    }))
        .build();
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

                              final CommandSender sender =
                                  ctx.getSource().getSender(); // Retrieve the command sender
                              final Entity executor =
                                  ctx.getSource()
                                      .getExecutor(); // Retrieve the command executor, which may or
                              // may not be the same as the sender

                              // Check whether the executor is a player, as you can only set a
                              // player's flight speed
                              if (!(executor instanceof Player player)) {
                                // If a non-player tried to set their own flight speed
                                sender.sendPlainMessage("Not a player");
                                return Command.SINGLE_SUCCESS;
                              }

                              boolean editSelf =
                                  targetPlayer.getUniqueId().equals(executor.getUniqueId());
                              boolean hasEditOthersPerm =
                                  sender.hasPermission("hookemitter" + ".join_msg.others");

                              if (!editSelf && !hasEditOthersPerm) {
                                sender.sendMessage(
                                    Component.text(
                                            "You don't have permission to set others' join messages.")
                                        .color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                              }

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
                              targetPlayer.sendMessage(
                                  Component.text(
                                      "HookEmitter: Join message for "
                                          + targetPlayer.getName()
                                          + " set to: "
                                          + message));
                              return Command.SINGLE_SUCCESS;
                            })))
        .build();
  }
}
