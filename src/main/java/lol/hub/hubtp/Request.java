package lol.hub.hubtp;

import org.bukkit.entity.Player;

public record Request(PlayerData target, PlayerData requester) {
    static Request of(PlayerData target, Player requester) {
        return new Request(target, PlayerData.of(requester));
    }

    boolean isSamePlayers(PlayerData target, Player requester) {
        return this.target.uuid().equals(target.uuid()) && this.requester.uuid().equals(requester.getUniqueId());
    }
}
