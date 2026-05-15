package com.turtle.tutiencore.core.command;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandAliasManager {

    private final JavaPlugin plugin;
    private final Map<String, Command> registeredAliases = new LinkedHashMap<>();

    public CommandAliasManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAliases(Map<String, List<String>> aliasesByCommand) {
        unregisterAliases();

        CommandMap commandMap = getCommandMap();
        Map<String, Command> knownCommands = getKnownCommands(commandMap);
        if (commandMap == null || knownCommands == null) {
            plugin.getLogger().warning("Could not access Bukkit command map; configurable aliases are disabled.");
            return;
        }

        for (Map.Entry<String, List<String>> entry : aliasesByCommand.entrySet()) {
            PluginCommand targetCommand = plugin.getCommand(entry.getKey());
            if (targetCommand == null) {
                plugin.getLogger().warning("Cannot register aliases for unknown command: " + entry.getKey());
                continue;
            }
            for (String rawAlias : entry.getValue()) {
                registerAlias(commandMap, knownCommands, targetCommand, rawAlias);
            }
        }
    }

    public void unregisterAliases() {
        if (registeredAliases.isEmpty()) {
            return;
        }

        CommandMap commandMap = getCommandMap();
        Map<String, Command> knownCommands = getKnownCommands(commandMap);
        if (commandMap != null && knownCommands != null) {
            for (Command aliasCommand : registeredAliases.values()) {
                aliasCommand.unregister(commandMap);
                knownCommands.entrySet().removeIf(entry -> entry.getValue() == aliasCommand);
            }
        }
        registeredAliases.clear();
    }

    private void registerAlias(CommandMap commandMap, Map<String, Command> knownCommands,
            PluginCommand targetCommand, String rawAlias) {
        String alias = normalizeAlias(rawAlias);
        if (alias == null || alias.equalsIgnoreCase(targetCommand.getName())) {
            return;
        }

        Command existing = knownCommands.get(alias);
        if (existing != null) {
            plugin.getLogger().warning("Cannot register alias /" + alias + " for /" + targetCommand.getName()
                    + " because it is already used by /" + existing.getName() + ".");
            return;
        }

        AliasCommand aliasCommand = new AliasCommand(alias, targetCommand);
        commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), aliasCommand);
        registeredAliases.put(alias, aliasCommand);
    }

    private String normalizeAlias(String rawAlias) {
        if (rawAlias == null) {
            return null;
        }

        String alias = rawAlias.trim();
        while (alias.startsWith("/")) {
            alias = alias.substring(1);
        }
        alias = alias.toLowerCase(Locale.ROOT);

        if (alias.isEmpty()) {
            return null;
        }
        if (!alias.matches("[a-z0-9_\\-]+")) {
            plugin.getLogger().warning("Ignoring invalid command alias '" + rawAlias
                    + "'. Use only letters, numbers, '_' or '-'.");
            return null;
        }
        return alias;
    }

    private CommandMap getCommandMap() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException | ClassCastException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read Bukkit command map.", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap commandMap) {
        if (commandMap == null) {
            return null;
        }
        try {
            Field field = findField(commandMap.getClass(), "knownCommands");
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            return (Map<String, Command>) field.get(commandMap);
        } catch (IllegalAccessException | ClassCastException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read Bukkit known commands.", ex);
            return null;
        }
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static final class AliasCommand extends Command {

        private final PluginCommand targetCommand;

        private AliasCommand(String alias, PluginCommand targetCommand) {
            super(alias);
            this.targetCommand = targetCommand;
            setDescription("Alias for /" + targetCommand.getName());
            setUsage("/" + alias);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return targetCommand.execute(sender, targetCommand.getName(), args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args)
                throws IllegalArgumentException {
            List<String> completions = targetCommand.tabComplete(sender, targetCommand.getName(), args);
            return completions != null ? completions : new ArrayList<>();
        }
    }
}
