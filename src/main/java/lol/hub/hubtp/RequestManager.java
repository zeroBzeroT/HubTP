package lol.hub.hubtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

public class RequestManager {
    private static final Map<Request, Long> pendingRequests = new ConcurrentHashMap<>();

    static void clearOldRequests(int timeoutValue) {
        long time = System.currentTimeMillis();
        pendingRequests.forEach((request, requestTime) -> {
            if (((time - requestTime) / 1000L) > timeoutValue) {
                pendingRequests.remove(request);
                Player requester = Bukkit.getPlayer(request.requester().uuid());
                if (requester != null) {
                    requester.sendMessage(
                            Component.text("Your teleport request to ", NamedTextColor.GOLD)
                                    .append(Component.text(request.target().name()))
                                    .append(Component.text(" timed out.")));
                }
                Player target = Bukkit.getPlayer(request.target().uuid());
                if (target != null) {
                    target.sendMessage(
                            Component.text("The teleport request from ", NamedTextColor.GOLD)
                                    .append(Component.text(request.requester().name()))
                                    .append(Component.text(" timed out.")));
                }
            }
        });
    }

    public static void addRequest(Player target, Player requester) {
        removeRequests(target, requester);
        pendingRequests.put(Request.of(target, requester), System.currentTimeMillis());
    }

    public static void removeRequests(Player target, Player requester) {
        for (Request request : pendingRequests.keySet()) {
            if (request.isSamePlayers(target, requester)) {
                pendingRequests.remove(request);
            }
        }
    }

    public static void cancelRequestsByTarget(Player target) {
        for (Request request : pendingRequests.keySet()) {
            if (request.target().uuid().equals(target.getUniqueId())) {
                pendingRequests.remove(request);
                // TODO: cancel info message
            }
        }
    }

    public static void cancelRequestsByRequester(Player requester) {
        cancelRequestsByRequesterInternal(requester, null, false);
    }

    public static List<Request> cancelAllRequestsByRequester(Player requester) {
        return cancelRequestsByRequesterInternal(requester, null, true);
    }

    public static List<Request> cancelRequestsByRequester(Player requester, String targetName) {
        return cancelRequestsByRequesterInternal(requester, targetName, true);
    }

    public static boolean isRequestActive(Player target, Player requester) {
        for (Request request : pendingRequests.keySet()) {
            if (request.isSamePlayers(target, requester)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRequestActiveByRequester(Player requester) {
        for (Request request : pendingRequests.keySet()) {
            if (request.requester().uuid().equals(requester.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private static List<Request> cancelRequestsByRequesterInternal(Player requester, @Nullable String targetName,
            boolean notifyTargets) {
        List<Request> toCancel = pendingRequests.keySet().stream()
                .filter(request -> request.requester().uuid().equals(requester.getUniqueId()))
                .filter(request -> targetName == null || request.target().name().equalsIgnoreCase(targetName))
                .collect(Collectors.toList());

        if (toCancel.isEmpty()) {
            return List.of();
        }

        Component targetMessage = Component.text(requester.getName(), NamedTextColor.GOLD)
                .append(Component.text(" cancelled their teleport request."));

        for (Request request : toCancel) {
            pendingRequests.remove(request);

            if (!notifyTargets) {
                continue;
            }

            Player target = Bukkit.getPlayer(request.target().uuid());
            if (target != null) {
                target.sendMessage(targetMessage);
            }
        }

        return List.copyOf(toCancel);
    }
}
