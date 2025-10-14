package lol.hub.hubtp;

import lol.hub.hubtp.util.Paths;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;

public final class Config {
    private static volatile boolean initialized = false;
    private static boolean allowMultiTargetRequest;
    private static int requestTimeoutSeconds;
    private static boolean spawnTpDeny;
    private static int spawnTpDenyRadius;
    private static boolean distanceLimit;
    private static int distanceLimitRadius;
    private static int tpDelaySeconds;
    private static boolean movementCheck;
    private static boolean includeLeashed;
    private static boolean includeLeashedInterdimensional;
    private static boolean teleportMountedEntities;
    private static Path ignoresPath;
    private static boolean debug;

    private static synchronized void assertInitialized() {
        if (!initialized)
            throw new IllegalStateException("Config access prior to initialization!");
    }

    public static synchronized void load(Plugin plugin) {
        FileConfiguration config = plugin.getConfig();

        config.addDefault("allow-multi-target-request", true);
        config.addDefault("request-timeout-seconds", 60);
        config.addDefault("spawn-tp-deny", true);
        config.addDefault("spawn-tp-deny-radius", 1500);
        config.addDefault("distance-limit", false);
        config.addDefault("distance-limit-radius", 10000);
        config.addDefault("tp-delay-seconds", 0);
        config.addDefault("movement-check", false);
        config.addDefault("include-leashed", true);
        config.addDefault("include-leashed-interdimensional", false);
        config.addDefault("teleport-mounted-entities", true);
        config.addDefault("ignores-path", Ignores.defaultPath.apply(plugin));
        config.addDefault("debug", false);
        config.addDefault("bStats", true);
        config.options().copyDefaults(true);
        plugin.saveConfig();

        allowMultiTargetRequest = config.getBoolean("allow-multi-target-request");

        if (config.getInt("request-timeout-seconds") < 10) {
            config.set("request-timeout-seconds", 10);
            plugin.saveConfig();
        }
        requestTimeoutSeconds = config.getInt("request-timeout-seconds");

        spawnTpDeny = config.getBoolean("spawn-tp-deny");

        if (config.getInt("spawn-tp-deny-radius") < 16) {
            config.set("spawn-tp-deny-radius", 16);
            plugin.saveConfig();
        }
        spawnTpDenyRadius = config.getInt("spawn-tp-deny-radius");

        distanceLimit = config.getBoolean("distance-limit");

        if (config.getInt("distance-limit-radius") < 16) {
            config.set("distance-limit-radius", 16);
            plugin.saveConfig();
        }
        distanceLimitRadius = config.getInt("distance-limit-radius");

        if (config.getInt("tp-delay-seconds") < 0) {
            config.set("tp-delay-seconds", 0);
            plugin.saveConfig();
        }
        tpDelaySeconds = config.getInt("tp-delay-seconds");

        movementCheck = config.getBoolean("movement-check");

        includeLeashed = config.getBoolean("include-leashed");

        includeLeashedInterdimensional = config.getBoolean("include-leashed-interdimensional");

        teleportMountedEntities = config.getBoolean("teleport-mounted-entities");

        // noinspection DataFlowIssue
        if (config.getString("ignores-path") == null || config.getString("ignores-path").isBlank()) {
            config.set("ignores-path", Ignores.defaultPath.apply(plugin));
            plugin.saveConfig();
        }

        if (!Paths.isValid(config.getString("ignores-path"))) {
            config.set("ignores-path", Ignores.defaultPath.apply(plugin));
            plugin.saveConfig();
        }
        // noinspection DataFlowIssue
        ignoresPath = Path.of(config.getString("ignores-path"));

        debug = config.getBoolean("debug");

        initialized = true;
    }

    public static boolean allowMultiTargetRequest() {
        assertInitialized();
        return allowMultiTargetRequest;
    }

    public static int requestTimeoutSeconds() {
        assertInitialized();
        return requestTimeoutSeconds;
    }

    public static boolean spawnTpDeny() {
        assertInitialized();
        return spawnTpDeny;
    }

    public static int spawnTpDenyRadius() {
        assertInitialized();
        return spawnTpDenyRadius;
    }

    public static boolean distanceLimit() {
        assertInitialized();
        return distanceLimit;
    }

    public static int distanceLimitRadius() {
        assertInitialized();
        return distanceLimitRadius;
    }

    public static int tpDelaySeconds() {
        assertInitialized();
        return tpDelaySeconds;
    }

    public static boolean movementCheck() {
        assertInitialized();
        return movementCheck;
    }

    public static boolean includeLeashed() {
        assertInitialized();
        return includeLeashed;
    }

    public static boolean includeLeashedInterdimensional() {
        assertInitialized();
        return includeLeashedInterdimensional;
    }

    public static boolean teleportMountedEntities() {
        assertInitialized();
        return teleportMountedEntities;
    }

    public static Path ignoresPath() {
        assertInitialized();
        return ignoresPath;
    }

    public static boolean debug() {
        assertInitialized();
        return debug;
    }
}
