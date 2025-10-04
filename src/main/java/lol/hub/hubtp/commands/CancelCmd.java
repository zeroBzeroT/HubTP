package lol.hub.hubtp.commands;

import java.util.List;

import lol.hub.hubtp.Plugin;
import lol.hub.hubtp.Request;
import lol.hub.hubtp.RequestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public class CancelCmd extends TpCommand {
    public CancelCmd(Plugin plugin, PluginCommand pluginCommand) {
        super(plugin, pluginCommand, 0, 1);
    }

    @Override
    public void run(Player commandSender, String targetName) {
        if (targetName == null) {
            List<Request> cancelled = RequestManager.cancelAllRequestsByRequester(commandSender);

            if (cancelled.isEmpty()) {
                commandSender.sendMessage(Component.text("You have no pending teleport requests to cancel.",
                        NamedTextColor.RED));
                return;
            }

            String suffix = cancelled.size() == 1 ? " teleport request." : " teleport requests.";
            commandSender.sendMessage(
                    Component.text("Cancelled ", NamedTextColor.GOLD)
                            .append(Component.text(cancelled.size(), NamedTextColor.GOLD))
                            .append(Component.text(suffix, NamedTextColor.GOLD)));
            return;
        }

        List<Request> cancelled = RequestManager.cancelRequestsByRequester(commandSender, targetName);

        if (cancelled.isEmpty()) {
            commandSender.sendMessage(
                    Component.text("You have no pending teleport request to ", NamedTextColor.RED)
                            .append(Component.text(targetName))
                            .append(Component.text(".")));
            return;
        }

        String resolvedTargetName = cancelled.get(0).target().name();
        commandSender.sendMessage(
                Component.text("Cancelled teleport request to ", NamedTextColor.GOLD)
                        .append(Component.text(resolvedTargetName, NamedTextColor.GOLD))
                        .append(Component.text(".", NamedTextColor.GOLD)));
    }
}
