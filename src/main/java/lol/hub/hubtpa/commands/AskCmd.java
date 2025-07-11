package lol.hub.hubtpa.commands;

import lol.hub.hubtpa.*;
import lol.hub.hubtpa.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

// tpa (tpask)
public class AskCmd extends TpCommand {
    public AskCmd(Plugin plugin, PluginCommand pluginCommand) {
        super(plugin, pluginCommand, 1);
    }

    @Override
    public void run(Player commandSender, String targetName) {
        var target = Players.getOnlinePlayer(plugin.getServer(), targetName);

        if (target == null) {
            commandSender.sendMessage(
                Component.text("Player ", NamedTextColor.RED)
                    .append(Component.text(targetName))
                    .append(Component.text(" is not online."))
            );
            return;
        }

        if (Ignores.get(target.getUniqueId(), commandSender.getUniqueId())) {
            commandSender.sendMessage(
                Component.text(target.getName(), NamedTextColor.RED)
                    .append(Component.text(" is ignoring your tpa requests!"))
            );
            return;
        }

        if (Ignores.get(commandSender.getUniqueId(), target.getUniqueId())) {
            commandSender.sendMessage(
                Component.text("You are ignoring ", NamedTextColor.RED)
                    .append(Component.text(target.getName()))
                    .append(Component.text(". Cannot send teleport requests."))
            );
            return;
        }

        if (Config.spawnTpDeny() && Players.isAtSpawn(commandSender)) {
            Log.debug("Denying teleport request while in spawn area from " + commandSender.getName() + " to " + target.getName() + ".");

            commandSender.sendMessage(
                Component.text("You are not allowed to teleport while in the spawn area!", NamedTextColor.RED)
            );
            return;
        }

        if (plugin.isRequestBlock(target)) {
            commandSender.sendMessage(
                Component.text(target.getName(), NamedTextColor.RED)
                    .append(Component.text(" is currently not accepting any teleport requests!"))
            );
            return;
        }

        if (plugin.isRequestBlock(commandSender)) {
            commandSender.sendMessage(
                Component.text("Unable to send teleport requests while ignoring incoming requests!", NamedTextColor.RED)
            );
            return;
        }

        if (Config.distanceLimit() &&
            Players.getOverworldXzVector(commandSender).distance(Players.getOverworldXzVector(target)) > Config.distanceLimitRadius()) {
            Log.debug("Denying teleport request while out of range from " + commandSender.getName() + " to " + target.getName() + ".");

            commandSender.sendMessage(
                Component.text("You are too far away from ", NamedTextColor.RED)
                    .append(Component.text(target.getName()))
                    .append(Component.text(" to teleport!"))
            );
            return;
        }

        if (RequestManager.isRequestActive(target, commandSender)) {
            commandSender.sendMessage(
                Component.text("Please wait for ", NamedTextColor.RED)
                    .append(Component.text(target.getName()))
                    .append(Component.text(" to accept or deny your request."))
            );
            return;
        }

        if (!Config.allowMultiTargetRequest() && RequestManager.isRequestActiveByRequester(commandSender)) {
            commandSender.sendMessage(
                Component.text("Please wait for your existing request to be accepted or denied.",
                    NamedTextColor.RED)
            );
            return;
        }

        commandSender.sendMessage(
            Component.text("Request sent to ", NamedTextColor.GOLD)
                .append(Component.text(target.getName()))
                .append(Component.text("."))
        );

        target.sendMessage(
            Component.text(commandSender.getName())
                .append(Component.text(" wants to teleport to you. ", NamedTextColor.GOLD))
                .append(
                    Component.text("[ACCEPT]", NamedTextColor.GREEN)
                        .hoverEvent(Component.text("Accept the teleport").asHoverEvent())
                        .clickEvent(ClickEvent.suggestCommand("/tpy " + commandSender.getName()))
                )
                .append(Component.text(" ", NamedTextColor.GOLD))
                .append(
                    Component.text("[DENY]", NamedTextColor.RED)
                        .hoverEvent(Component.text("Deny the teleport").asHoverEvent())
                        .clickEvent(ClickEvent.suggestCommand("/tpn " + commandSender.getName()))
                )
                .append(Component.text(" ", NamedTextColor.GOLD))
                .append(
                    Component.text("[IGNORE]", NamedTextColor.GRAY)
                        .hoverEvent(Component.text("Ignore the requester").asHoverEvent())
                        .clickEvent(ClickEvent.suggestCommand("/tpi " + commandSender.getName()))
                )
        );

        RequestManager.addRequest(target, commandSender);
    }
}
