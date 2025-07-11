package lol.hub.hubtpa.commands;

import lol.hub.hubtpa.Plugin;
import lol.hub.hubtpa.RequestManager;
import lol.hub.hubtpa.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

// tpn (tpdeny)
public class DenyCmd extends TpCommand {
    public DenyCmd(Plugin plugin, PluginCommand pluginCommand) {
        super(plugin, pluginCommand, 1);
    }

    @Override
    public void run(Player tpTarget, String requesterName) {
        var tpRequester = Players.getOnlinePlayer(plugin.getServer(), requesterName);

        if (tpRequester == null) {
            tpTarget.sendMessage(
                Component.text("Player ", NamedTextColor.RED)
                    .append(Component.text(requesterName))
                    .append(Component.text(" is not online."))
            );
            return;
        }

        if (!RequestManager.isRequestActive(tpTarget, tpRequester)) {
            tpTarget.sendMessage(
                Component.text("There is no request to deny from ", NamedTextColor.RED)
                    .append(Component.text(tpRequester.getName()))
                    .append(Component.text("!"))
            );
            return;
        }

        tpTarget.sendMessage(
            Component.text("Request from ", NamedTextColor.GOLD)
                .append(Component.text(tpRequester.getName()))
                .append(Component.text(" denied", NamedTextColor.RED))
                .append(Component.text("!", NamedTextColor.GOLD))
        );
        tpRequester.sendMessage(
            Component.text("Your request sent to ", NamedTextColor.GOLD)
                .append(Component.text(tpTarget.getName()))
                .append(Component.text(" was"))
                .append(Component.text(" denied", NamedTextColor.RED))
                .append(Component.text("!", NamedTextColor.GOLD))
        );

        // TODO: wrap this method in a "deny" call
        RequestManager.removeRequests(tpTarget, tpRequester);
    }
}
