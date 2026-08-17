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
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
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
            @NotNull String commandLabel, String @NotNull [] args) {
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

        // /tpy runs on tpTarget's region; hop to tpRequester's region so
        // getVehicle() and getNearbyEntities() are region-safe
        this.foliaLib.getScheduler().runAtEntity(tpRequester, task -> {
            boolean freeSeat = Config.teleportIntoFreeSeat()
                    && tpRequester.getVehicle() == null
                    && tpTarget.getVehicle() instanceof Vehicle targetVehicle
                    && isDriverOf(tpTarget, targetVehicle)
                    && targetVehicle.getPassengers().size() < maxPassengersFor(targetVehicle);
            boolean leashScan = Config.includeLeashed() && shouldTpLeashed(tpTarget, tpRequester);

            if (freeSeat) {
                teleportIntoFreeSeat(tpTarget, tpRequester,
                        (Vehicle) tpTarget.getVehicle());
            } else if (leashScan) {
                List<LivingEntity> leashed = tpRequester.getWorld()
                        .getNearbyEntities(tpRequester.getLocation(), 16, 16, 16).stream()
                        .filter(e -> e instanceof LivingEntity)
                        .map(e -> (LivingEntity) e)
                        .filter(LivingEntity::isLeashed)
                        .filter(e -> e.getLeashHolder() != null
                                && leashHolderMatches(e, tpRequester))
                        .toList();
                for (LivingEntity mob : leashed) {
                    teleportLeashedMob(mob, tpTarget.getLocation());
                }
                doTeleportAndNotify(tpTarget, tpRequester);
            } else {
                doTeleportAndNotify(tpTarget, tpRequester);
            }
        });
    }

    private boolean leashHolderMatches(LivingEntity mob, Player requester) {
        Entity holder = mob.getLeashHolder();
        if (holder == null) return false;
        if (holder.getUniqueId().equals(requester.getUniqueId())) return true;
        if (!Config.teleportLeashedOfPassengers()) return false;
        Entity mount = requester.getVehicle();
        if (!(mount instanceof Vehicle v)) return false;
        for (Entity passenger : v.getPassengers()) {
            if (passenger.getUniqueId().equals(holder.getUniqueId())) return true;
        }
        return false;
    }

    private void teleportLeashedMob(LivingEntity mob, Location destination) {
        Entity holder = mob.getLeashHolder();
        mob.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((ok, ex) -> {
                    if (ok == null || !ok || ex != null || holder == null) return;
                    this.foliaLib.getScheduler().runAtEntity(holder, task -> {
                        if (!mob.isValid() || !holder.isValid()) return;
                        if (!mob.getWorld().equals(holder.getWorld())) return;
                        mob.setLeashHolder(holder);
                        // we drop a lead on teleport; clean it up so the player
                        // doesn't end up with two (one in item form, one attached to entity)
                        removeNearbyDroppedLeads(mob.getLocation(), 1);
                    });
                });
    }

    private void removeNearbyDroppedLeads(Location center, int count) {
        for (Entity e : center.getWorld().getNearbyEntities(center, 2, 2, 2)) {
            if (count <= 0) return;
            if (e instanceof Item item && item.getItemStack().getType() == Material.LEAD) {
                item.remove();
                count--;
            }
        }
    }

    private void teleportIntoFreeSeat(Player tpTarget, Player tpRequester, Vehicle vehicle) {
        Location dest = tpTarget.getLocation().clone();
        tpRequester.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.COMMAND)
                .whenComplete((ok, ex) -> {
                    if (ok != null && ok && ex == null) {
                        // addPassenger must run on the vehicle's owning region (Folia)
                        this.foliaLib.getScheduler().runAtEntity(vehicle, task -> {
                            if (vehicle.isValid() && tpRequester.isOnline()
                                    && vehicle.getPassengers().size() < maxPassengersFor(vehicle)) {
                                vehicle.addPassenger(tpRequester);
                            }
                        });
                        sendOnRegion(tpTarget, Component.text(tpRequester.getName())
                                .append(Component.text(" teleported to you and hopped in.", NamedTextColor.GOLD)));
                        sendOnRegion(tpRequester, Component.text("Teleported to ", NamedTextColor.GOLD)
                                .append(Component.text(tpTarget.getName()))
                                .append(Component.text(" and hopped in.", NamedTextColor.GOLD)));
                    } else {
                        if (ex != null) {
                            Log.warn("Teleport-into-seat failed: " + ex.getMessage());
                        }
                        TextComponent msg = Component.text("Teleportation failed.", NamedTextColor.RED);
                        sendOnRegion(tpTarget, msg);
                        sendOnRegion(tpRequester, msg);
                    }
                });
    }

    private static int maxPassengersFor(Vehicle vehicle) {
        if (vehicle instanceof Boat) return 2;
        if (vehicle instanceof Camel) return 2;
        return 1;
    }

    private void doTeleportAndNotify(Player tpTarget, Player tpRequester) {
        Location destination = tpTarget.getLocation().clone();
        boolean crossWorld = !tpRequester.getWorld().equals(tpTarget.getWorld());

        // folia: vehicle/mount state lives in requester's region. hop there first.
        this.foliaLib.getScheduler().runAtEntity(tpRequester, task -> {
            // reject if requester and target share a vehicle
            if (sharesVehicleWithRequester(tpRequester, tpTarget)) {
                sendOnRegion(tpTarget, Component.text(tpRequester.getName(), NamedTextColor.RED)
                        .append(Component.text(" and you are in the same vehicle. The request was cancelled.", NamedTextColor.RED)));
                sendOnRegion(tpRequester, Component.text(
                        "Teleportation cancelled: you and ", NamedTextColor.RED)
                        .append(Component.text(tpTarget.getName(), NamedTextColor.RED))
                        .append(Component.text(" are in the same vehicle.", NamedTextColor.RED)));
                return;
            }

            CompletableFuture<Boolean> result = teleportRequesterWithVehicle(tpRequester, destination, crossWorld);
            result.whenComplete((success, ex) -> {
                boolean ok = success != null && success && ex == null;
                if (ok) {
                    sendOnRegion(tpTarget, Component.text(tpRequester.getName())
                            .append(Component.text(" teleported to you!", NamedTextColor.GOLD)));
                    sendOnRegion(tpRequester, Component.text("Teleported to ", NamedTextColor.GOLD)
                            .append(Component.text(tpTarget.getName()))
                            .append(Component.text("!")));
                } else {
                    if (ex != null) {
                        Log.warn("Teleport failed with exception: " + ex.getMessage());
                    }
                    TextComponent msg = Component.text(
                            "Teleportation failed.",
                            NamedTextColor.RED);
                    sendOnRegion(tpTarget, msg);
                    sendOnRegion(tpRequester, msg);
                }
            });
        });
    }

    private static boolean sharesVehicleWithRequester(Player requester, Player target) {
        Entity mount = requester.getVehicle();
        if (!(mount instanceof Vehicle v)) return false;
        for (Entity passenger : v.getPassengers()) {
            if (passenger.getUniqueId().equals(target.getUniqueId())) return true;
        }
        return false;
    }

    public void sendOnRegion(Player player, Component message) {
        if (player.isOnline()) {
            this.foliaLib.getScheduler().runAtEntity(player, task -> player.sendMessage(message));
        }
    }

    private CompletableFuture<Boolean> teleportRequesterWithVehicle(Player requester, Location destination,
            boolean crossWorld) {
        Entity mount = requester.getVehicle();

        if (mount == null) {
            return requester.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        }

        // passengers dismount and tp alone. only the driver brings the vehicle
        if (!isDriverOf(requester, mount)) {
            requester.leaveVehicle();
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
                PlayerTeleportEvent.TeleportCause cause = passenger instanceof Player
                        ? PlayerTeleportEvent.TeleportCause.COMMAND
                        : PlayerTeleportEvent.TeleportCause.PLUGIN;
                teleportFutures.add(passenger.teleportAsync(destination, cause));
            }

            return combineFutures(teleportFutures)
                    .thenApply(success -> {
                        if (success) {
                            // remount on the mounts (now destination) region
                            this.foliaLib.getScheduler().runAtEntity(mount,
                                    task -> remountPassengers(mount, passengerSnapshots));
                        }
                        return success;
                    });
        }

        // same dim: RETAIN_PASSENGERS already moves passengers, don't double tp them
        List<CompletableFuture<Boolean>> teleportFutures = new ArrayList<>();
        teleportFutures.add(mount.teleportAsync(destination.clone(),
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.EntityState.RETAIN_PASSENGERS));

        return combineFutures(teleportFutures)
                .thenApply(success -> {
                    if (success) {
                        this.foliaLib.getScheduler().runAtEntity(mount,
                                task -> remountPassengers(mount, passengerSnapshots));
                    }
                    return success;
                });
    }

    /**
     * first Player in the passenger list is the "driver"
     */
    private static boolean isDriverOf(Player requester, Entity vehicle) {
        if (!(vehicle instanceof Vehicle v)) return false;
        for (Entity passenger : v.getPassengers()) {
            if (passenger instanceof Player) {
                return passenger.getUniqueId().equals(requester.getUniqueId());
            }
        }
        return true;
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

        // getNow() rethrows on exceptional completion, masking the overall result
        List<CompletableFuture<Boolean>> snapshot = List.copyOf(futures);
        return CompletableFuture.allOf(snapshot.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> snapshot.stream().allMatch(f -> {
                    if (f.isCompletedExceptionally()) return false;
                    return Boolean.TRUE.equals(f.getNow(false));
                }));
    }

    /**
     * Clears old teleport requests based on configured timeout.
     */
    public void clearOldRequests() {
        RequestManager.clearOldRequests(this, Config.requestTimeoutSeconds());
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
