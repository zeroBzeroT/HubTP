package lol.hub.hubtp.commands;

import lol.hub.hubtp.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public abstract class TpCommand {
    public final String usage;
    final Plugin plugin;
    private final int minArguments;
    private final int maxArguments;

    public TpCommand(Plugin plugin, String usage, int minArguments, int maxArguments) {
        this.plugin = plugin;
        this.usage = usage;
        this.minArguments = minArguments;
        this.maxArguments = maxArguments;
    }

    public TpCommand(Plugin plugin, String usage, int argumentCount) {
        this(plugin, usage, argumentCount, argumentCount);
    }

    public TpCommand(Plugin plugin, PluginCommand pluginCommand, int argumentCount) {
        this(plugin, pluginCommand.getUsage(), argumentCount, argumentCount);
    }

    public TpCommand(Plugin plugin, PluginCommand pluginCommand, int minArguments, int maxArguments) {
        this(plugin, pluginCommand.getUsage(), minArguments, maxArguments);
    }

    public boolean isArgumentCountValid(int count) {
        return count >= minArguments && count <= maxArguments;
    }

    public abstract void run(Player commandSender, String targetName);

    public void sendUsage(Player player) {
        player.sendMessage(Component.text("Usage: " + usage, NamedTextColor.GOLD));
    }
}
