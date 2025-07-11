package lol.hub.hubtpa.commands;

import lol.hub.hubtpa.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public abstract class TpCommand {
    public final String usage;
    final Plugin plugin;
    private final int argumentCount;

    public TpCommand(Plugin plugin, String usage, int argumentCount) {
        this.plugin = plugin;
        this.usage = usage;
        this.argumentCount = argumentCount;
    }

    public TpCommand(Plugin plugin, PluginCommand pluginCommand, int argumentCount) {
        this(plugin, pluginCommand.getUsage(), argumentCount);
    }

    public int getArgumentCount() {
        return argumentCount;
    }

    public abstract void run(Player commandSender, String targetName);

    public void sendUsage(Player player) {
        player.sendMessage(Component.text("Usage: " + usage, NamedTextColor.GOLD));
    }
}
