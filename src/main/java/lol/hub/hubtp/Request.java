package lol.hub.hubtp;

import org.bukkit.entity.Player;

public record Request(PlayerData target, PlayerData requester) {
    static Request of(Player target, Player requester) {
        return new Request(PlayerData.of(target), PlayerData.of(requester));
    }

    boolean isSamePlayers(Player target, Player requester) {
        return this.target.uuid().equals(target.getUniqueId()) && this.requester.uuid().equals(requester.getUniqueId());
    }
}
