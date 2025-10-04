package lol.hub.hubtp;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import io.papermc.paper.entity.TeleportFlag;
import lol.hub.hubtp.commands.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    private final Map<String, Function<PluginCommand, TpCommand>> commandMap = Map.ofEntries(
            Map.entry("tpa", pCmd -> new AskCmd(this, pCmd)),
            Map.entry("tpy", pCmd -> new AcceptCmd(this, pCmd)),
            Map.entry("tpn", pCmd -> new DenyCmd(this, pCmd)),
            Map.entry("tpt", pCmd -> new ToggleCmd(this, pCmd)),
            Map.entry("tpi", pCmd -> new IgnoreCmd(this, pCmd)),
            Map.entry("tpc", pCmd -> new CancelCmd(this, pCmd)));

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
     * Called when the plugin is enabled. Sets up commands, metrics, and event
     * listeners.
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
                if (!Config.movementCheck())
                    return;
                if (!event.hasChangedPosition())
                    return;
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
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command,
            @NotNull String commandLabel, String[] args) {
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
        if (!commands.get(commandLabel).isArgumentCountValid(args.length)) {
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
        if (tpTarget == null || tpRequester == null) {
            return;
        }

        int tpDelay = Config.tpDelaySeconds();
        if (tpDelay > 0) {
            // Notify players about the pending teleport
            tpTarget.sendMessage(Component.text("Teleporting ", NamedTextColor.GOLD)
                    .append(Component.text(tpRequester.getName()))
                    .append(Component.text(" in "))
                    .append(Component.text(tpDelay))
                    .append(Component.text(" seconds...")));

            tpRequester.sendMessage(Component.text("Teleporting in ", NamedTextColor.GOLD)
                    .append(Component.text(tpDelay))
                    .append(Component.text(" seconds...")));

            this.getScheduler().runLaterAsync(() -> {
                if (RequestManager.isRequestActive(tpTarget, tpRequester)) {
                    this.executeTPMove(tpTarget, tpRequester);
                }
            }, (long) tpDelay * 20L);
        } else {
            // Immediate teleport
            this.executeTPMove(tpTarget, tpRequester);
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
                    .forEach(entity -> entity.teleportAsync(tpTarget.getLocation(),
                            PlayerTeleportEvent.TeleportCause.PLUGIN));
        }

        Location destination = tpTarget.getLocation().clone();
        boolean crossWorld = !tpRequester.getWorld().equals(tpTarget.getWorld());

        // Execute teleport asynchronously, accounting for mounts
        teleportRequesterWithVehicle(tpRequester, destination, crossWorld)
                .thenAccept(result -> {
                    if (result) {
                        tpTarget.sendMessage(
                                Component.text(tpRequester.getName())
                                        .append(Component.text(" teleported to you!", NamedTextColor.GOLD)));
                        tpRequester.sendMessage(
                                Component.text("Teleported to ", NamedTextColor.GOLD)
                                        .append(Component.text(tpTarget.getName()))
                                        .append(Component.text("!")));
                    } else {
                        TextComponent msg = Component.text(
                                "Teleportation failed.",
                                NamedTextColor.RED);
                        tpTarget.sendMessage(msg);
                        tpRequester.sendMessage(msg);
                    }
                });
    }

    private CompletableFuture<Boolean> teleportRequesterWithVehicle(Player requester, Location destination,
            boolean crossWorld) {
        Entity mount = requester.getVehicle();

        if (mount == null) {
            return requester.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        }

        if (!Config.teleportMountedEntities()) {
            requester.leaveVehicle();
            return requester.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        }

        List<PassengerSnapshot> passengerSnapshots = collectPassengerTree(mount);

        if (crossWorld) {
            requester.leaveVehicle();
            passengerSnapshots.stream()
                    .map(PassengerSnapshot::entity)
                    .forEach(Entity::leaveVehicle);

            List<CompletableFuture<Boolean>> teleportFutures = new ArrayList<>();
            teleportFutures.add(mount.teleportAsync(destination.clone(),
                    PlayerTeleportEvent.TeleportCause.PLUGIN));

            for (PassengerSnapshot snapshot : passengerSnapshots) {
                Entity passenger = snapshot.entity();
                Location target = destination.clone();
                PlayerTeleportEvent.TeleportCause cause = passenger instanceof Player
                        ? PlayerTeleportEvent.TeleportCause.COMMAND
                        : PlayerTeleportEvent.TeleportCause.PLUGIN;
                teleportFutures.add(passenger.teleportAsync(target, cause));
            }

            return combineFutures(teleportFutures)
                    .thenApply(success -> {
                        if (success) {
                            remountPassengers(mount, passengerSnapshots);
                        }
                        return success;
                    });
        }

        List<CompletableFuture<Boolean>> teleportFutures = new ArrayList<>();
        teleportFutures.add(mount.teleportAsync(destination.clone(),
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.EntityState.RETAIN_PASSENGERS));

        for (PassengerSnapshot snapshot : passengerSnapshots) {
            Entity passenger = snapshot.entity();
            PlayerTeleportEvent.TeleportCause cause = passenger instanceof Player
                    ? PlayerTeleportEvent.TeleportCause.COMMAND
                    : PlayerTeleportEvent.TeleportCause.PLUGIN;
            teleportFutures.add(passenger.teleportAsync(destination.clone(),
                    cause,
                    TeleportFlag.EntityState.RETAIN_VEHICLE));
        }

        return combineFutures(teleportFutures)
                .thenApply(success -> {
                    if (success) {
                        remountPassengers(mount, passengerSnapshots);
                    }
                    return success;
                });
    }

    private static void remountPassengers(Entity root, List<PassengerSnapshot> snapshots) {
        Map<Entity, List<Entity>> parentToChildren = new HashMap<>();

        for (PassengerSnapshot snapshot : snapshots) {
            Entity parent = snapshot.vehicle();
            Entity passenger = snapshot.entity();

            if (parent == null || !parent.isValid() || !passenger.isValid()) {
                continue;
            }

            parentToChildren.computeIfAbsent(parent, key -> new ArrayList<>()).add(passenger);
        }

        Deque<Entity> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            Entity current = queue.poll();
            List<Entity> children = parentToChildren.get(current);

            if (children == null) {
                continue;
            }

            for (Entity child : children) {
                if (!child.isValid() || !current.isValid()) {
                    continue;
                }

                if (child.getVehicle() != current) {
                    if (child.getVehicle() != null) {
                        child.leaveVehicle();
                    }
                    current.addPassenger(child);
                }

                queue.add(child);
            }
        }
    }

    private static List<PassengerSnapshot> collectPassengerTree(Entity root) {
        List<PassengerSnapshot> passengers = new ArrayList<>();
        Deque<PassengerSnapshot> queue = new ArrayDeque<>();

        for (Entity passenger : root.getPassengers()) {
            queue.add(new PassengerSnapshot(passenger, root));
        }

        while (!queue.isEmpty()) {
            PassengerSnapshot snapshot = queue.poll();
            passengers.add(snapshot);

            for (Entity child : snapshot.entity().getPassengers()) {
                queue.add(new PassengerSnapshot(child, snapshot.entity()));
            }
        }

        return passengers;
    }

    private record PassengerSnapshot(Entity entity, Entity vehicle) {
    }

    private static CompletableFuture<Boolean> combineFutures(List<CompletableFuture<Boolean>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<?>[] futureArray = futures.toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(futureArray)
                .thenApply(ignored -> futures.stream().allMatch(future -> future.getNow(false)));
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
     * Determines if teleporting between two entities should consider leashed
     * status.
     */
    private static boolean shouldTpLeashed(Entity playerA, Entity playerB) {
        return playerA.getWorld().getEnvironment() == playerB.getWorld().getEnvironment()
                || Config.includeLeashedInterdimensional();
    }
}
