package lol.hub.hubtp.commands;

import lol.hub.hubtp.Plugin;
import lol.hub.hubtp.RequestManager;
import lol.hub.hubtp.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

// tpy (tpaccept)
public class AcceptCmd extends TpCommand {
    public AcceptCmd(Plugin plugin, PluginCommand pluginCommand) {
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
                Component.text("There is no request to accept from ", NamedTextColor.RED)
                    .append(Component.text(tpRequester.getName()))
                    .append(Component.text("!"))
            );
            return;
        }

        tpTarget.sendMessage(
            Component.text("Request from ", NamedTextColor.GOLD)
                .append(Component.text(tpRequester.getName()))
                .append(Component.text(" accepted", NamedTextColor.GREEN))
                .append(Component.text("!", NamedTextColor.GOLD))
        );

        tpRequester.sendMessage(
            Component.text("Your request was ", NamedTextColor.GOLD)
                .append(Component.text("accepted", NamedTextColor.GREEN))
                .append(Component.text(". Teleporting to ", NamedTextColor.GOLD))
                .append(Component.text(tpTarget.getName()))
                .append(Component.text("."))
        );

        // TODO: combine these 2 methods to a single "accept" call
        plugin.executeTP(tpTarget, tpRequester);
        RequestManager.removeRequests(tpTarget, tpRequester);
    }
}
