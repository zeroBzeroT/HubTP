package lol.hub.hubtpa.commands;

import lol.hub.hubtpa.Ignores;
import lol.hub.hubtpa.Plugin;
import lol.hub.hubtpa.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

// tpi (tpignore)
public class IgnoreCmd extends TpCommand {
    public IgnoreCmd(Plugin plugin, PluginCommand pluginCommand) {
        super(plugin, pluginCommand, 1);
    }

    @Override
    public void run(Player commandSender, String targetName) {
        var targetUuid = Players.getPlayerUUID(plugin.getServer(), targetName);

        if (targetUuid == null) {
            commandSender.sendMessage(
                Component.text("Player ", NamedTextColor.RED)
                    .append(Component.text(targetName))
                    .append(Component.text(" not found."))
            );
            return;
        }

        if (Ignores.get(commandSender.getUniqueId(), targetUuid)) {
            Ignores.set(commandSender.getUniqueId(), targetUuid, false);
            commandSender.sendMessage(
                Component.text("No longer ignoring teleport requests from ", NamedTextColor.GOLD)
                    .append(Component.text(targetName))
                    .append(Component.text("."))
            );
        } else {
            boolean success = Ignores.set(commandSender.getUniqueId(), targetUuid, true);

            if (success) {
                commandSender.sendMessage(
                    Component.text("Ignoring teleport requests from ", NamedTextColor.GOLD)
                        .append(Component.text(targetName))
                        .append(Component.text("."))
                );
            } else {
                commandSender.sendMessage(
                    Component.text("Maximum number of ignores reached.", NamedTextColor.RED)
                );
            }
        }
    }
}
