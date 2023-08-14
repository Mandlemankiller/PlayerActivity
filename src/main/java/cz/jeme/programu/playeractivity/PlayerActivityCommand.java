package cz.jeme.programu.playeractivity;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PlayerActivityCommand extends Command {
    private final PlayerActivity plugin;

    protected PlayerActivityCommand(PlayerActivity plugin) {
        super("playeractivity", "Main command for PlayerActivity", "false", List.of("pa"));
        setPermission("playeractivity.playeractivity");
        Bukkit.getCommandMap().register("playeractivity", this);

        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Messages.prefix("<red>Not enough arguments!</red>"));
            return true;
        }
        if (args[0].equals("reload")) {
            plugin.reload();
            sender.sendMessage(Messages.prefix("<green>Plugin reloaded successfully!</green>"));
            return true;
        }
        sender.sendMessage(Messages.prefix("<red>Unknown command!</red>"));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            return containsFilter(List.of("reload"), args[0]);
        }
        return Collections.emptyList();
    }

    private static List<String> containsFilter(List<String> list, String mark) {
        return list.stream()
                .filter(item -> item.contains(mark))
                .toList();
    }
}
