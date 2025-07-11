package lol.hub.hubtpa;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import lol.hub.hubtpa.commands.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main plugin class for handling teleport requests and commands.
 */
public class Plugin extends JavaPlugin {
    /**
     * Prefix used for blocked requests.
     */
    public static final String BLOCKED_PREFIX = "requests-blocked-";

    /**
     * Map storing command labels to their corresponding TpCommand instances.
     */
    private final Map<String, TpCommand> commands = new HashMap<>();

    /**
     * FoliaLib instance for scheduler and platform-specific features.
     */
    private FoliaLib foliaLib;

    /**
     * Maps associating command labels with their constructor functions.
     */
    private final Map<String, Function<PluginCommand, TpCommand>> commandMap = Map.of(
        "tpa", pCmd -> new AskCmd(this, pCmd),
        "tpy", pCmd -> new AcceptCmd(this, pCmd),
        "tpn", pCmd -> new DenyCmd(this, pCmd),
        "tpt", pCmd -> new ToggleCmd(this, pCmd),
        "tpi", pCmd -> new IgnoreCmd(this, pCmd)
    );

    /**
     * Gets the scheduler instance from FoliaLib.
     */
    public PlatformScheduler getScheduler() {
        return this.foliaLib.getScheduler();
    }

    /**
     * Called when the plugin is loaded. Initializes FoliaLib.
     */
    public void onLoad() {
        this.foliaLib = new FoliaLib(this);
    }

    /**
     * Retrieves all plugin commands registered by this plugin.
     */
    public Set<PluginCommand> getPluginCommands() {
        return getServer()
            .getCommandMap()
            .getKnownCommands()
            .values()
            .stream()
            .filter(org.bukkit.command.Command::isRegistered)
            .filter(cmd -> cmd instanceof PluginCommand)
            .map(cmd -> (PluginCommand) cmd)
            .filter(cmd -> cmd.getPlugin() == this)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Called when the plugin is enabled. Sets up commands, metrics, and event listeners.
     */
    public void onEnable() {
        Log.set(this.getLogger());

        Config.load(this);

        // Load Plugin Metrics if enabled
        if (getConfig().getBoolean("bStats")) {
            new Metrics(this, 11798);
        }

        // Register commands based on commandMap
        for (PluginCommand pCmd : getPluginCommands()) {
            String label = pCmd.getLabel().toLowerCase();

            if (commandMap.containsKey(label)) {
                registerTpCommand(label, pCmd, commandMap.get(label));
            } else {
                throw new IllegalStateException("Unknown command: " + pCmd.getLabel());
            }
        }

        // Register movement event to cancel requests on movement
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerMove(PlayerMoveEvent event) {
                if (!Config.movementCheck()) return;
                if (!event.hasChangedPosition()) return;
                RequestManager.cancelRequestsByRequester(event.getPlayer());
            }
        }, this);

        // Schedule periodic cleanup of old requests
        this.getScheduler().runTimer(this::clearOldRequests, 20L, 20L);
    }

    /**
     * Helper method to register a command and its aliases.
     */
    private void registerTpCommand(String label, PluginCommand pCmd, Function<PluginCommand, TpCommand> constructor) {
        TpCommand cmdInstance = constructor.apply(pCmd);
        commands.put(label, cmdInstance);

        for (String alias : pCmd.getAliases()) {
            commands.put(alias.toLowerCase(), cmdInstance);
        }
    }

    /**
     * Handles command execution for registered teleport commands.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String commandLabel, String[] args) {
        // Stop console access
        if (!(commandSender instanceof Player sender)) {
            Log.warn("Ignoring command executed by non-player sender: " + commandSender.getName());
            return false;
        }

        // Check if the command is in the command map
        if (!commands.containsKey(commandLabel)) {
            Log.warn("Unknown command: " + commandLabel);
            return false;
        }

        // Force the right argument count
        if (commands.get(commandLabel).getArgumentCount() != args.length) {
            commands.get(commandLabel).sendUsage(sender);
            return true;
        }

        String parameter = args.length > 0 ? args[0] : null;

        // Prevent players from teleporting to themselves
        if (parameter != null && sender.getName().equalsIgnoreCase(parameter)) {
            sender.sendMessage(Component.text("You cannot use this command on yourself.", NamedTextColor.RED));
            return true;
        }

        // Run the command with the sender and target name
        commands.get(commandLabel).run(sender, parameter);
        return true;
    }

    /**
     * Executes a teleport from the requester to the target, with optional delay.
     */
    public void executeTP(Player tpTarget, Player tpRequester) {
        if (tpTarget != null && tpRequester != null) {
            if (tpTarget.getVehicle() == null && tpRequester.getVehicle() == null) {
                int tpDelay = Config.tpDelaySeconds();
                if (tpDelay > 0) {
                    // Notify players about the pending teleport
                    tpTarget.sendMessage(Component.text("Teleporting ", NamedTextColor.GOLD)
                        .append(Component.text(tpRequester.getName()))
                        .append(Component.text(" in ", NamedTextColor.GOLD))
                        .append(Component.text(tpDelay))
                        .append(Component.text(" seconds...", NamedTextColor.GOLD)));

                    tpRequester.sendMessage(Component.text("Teleporting in ", NamedTextColor.GOLD)
                        .append(Component.text(tpDelay))
                        .append(Component.text(" seconds...", NamedTextColor.GOLD)));

                    this.getScheduler().runLaterAsync(() -> {
                        if (RequestManager.isRequestActive(tpTarget, tpRequester)) {
                            this.executeTPMove(tpTarget, tpRequester);
                        }

                    }, (long) tpDelay * 20L);
                } else {
                    // Immediate teleport
                    this.executeTPMove(tpTarget, tpRequester);
                }

            } else {
                // Teleportation failed due to vehicles
                TextComponent msg = Component.text("Teleport failed!", NamedTextColor.RED);
                tpTarget.sendMessage(msg);
                tpRequester.sendMessage(msg);
            }
        }
    }

    /**
     * Performs the actual teleportation of the requester to the target.
     */
    public void executeTPMove(Player tpTarget, Player tpRequester) {
        String requesterName = tpRequester.getName();
        Log.info("Teleporting " + requesterName + " to " + tpTarget.getName());

        // Leash handling if enabled
        if (Config.includeLeashed() && shouldTpLeashed(tpTarget, tpRequester)) {
            tpRequester.getWorld()
                .getNearbyEntities(tpRequester.getLocation(), 16, 16, 16).stream()
                .filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e)
                .filter(LivingEntity::isLeashed)
                .filter(e -> e.getLeashHolder().getUniqueId().equals(tpRequester.getUniqueId()))
                .forEach(entity ->
                    entity.teleportAsync(tpTarget.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN));
        }

        // Execute teleport asynchronously
        tpRequester.teleportAsync(tpTarget.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND)
            .thenAccept(result -> {
                if (result) {
                    tpTarget.sendMessage(
                        Component.text(tpRequester.getName())
                            .append(Component.text(" teleported to you!", NamedTextColor.GOLD)));
                    tpRequester.sendMessage(
                        Component.text("Teleported to ", NamedTextColor.GOLD)
                            .append(Component.text(tpTarget.getName()))
                            .append(Component.text("!", NamedTextColor.GOLD)));
                } else {
                    TextComponent msg =
                        Component.text("Teleport failed, you should harass your admin because of this!", NamedTextColor.RED);
                    tpTarget.sendMessage(msg);
                    tpRequester.sendMessage(msg);
                }
            });
    }

    /**
     * Clears old teleport requests based on configured timeout.
     */
    public void clearOldRequests() {
        RequestManager.clearOldRequests(Config.requestTimeoutSeconds());
    }

    /**
     * Checks if requests are blocked in the config for a specific player.
     */
    public boolean isRequestBlock(Player player) {
        return this.getConfig().getBoolean("requests-blocked-" + player.getUniqueId());
    }

    /**
     * Determines if teleporting between two entities should consider leashed status.
     */
    private static boolean shouldTpLeashed(Entity playerA, Entity playerB) {
        return playerA.getWorld().getEnvironment() == playerB.getWorld().getEnvironment() || Config.includeLeashedInterdimensional();
    }
}
