package com.graveguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class GraveGuardPlugin extends JavaPlugin {
    private GraveManager manager;
    private Config cfg;

    public enum XpMode { FULL, VANILLA_DROP, NONE }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        this.manager = new GraveManager(this, cfg);
        getServer().getPluginManager().registerEvents(new GraveListener(manager), this);
        new GraveTask(manager).runTaskTimer(this, 20L, 20L);
        getLogger().info("GraveGuard enabled");
    }

    private void load() {
        var c = getConfig();
        String xpSetting = c.getString("grave.xp-mode",
                c.getBoolean("grave.store-xp", true) ? "FULL" : "NONE");
        XpMode xpMode;
        try {
            xpMode = XpMode.valueOf(xpSetting.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Unknown grave.xp-mode '" + xpSetting + "'; using FULL");
            xpMode = XpMode.FULL;
        }
        this.cfg = new Config(
            c.getLong("grave.protection-seconds", 120),
            c.getLong("grave.despawn-seconds", 600),
            c.getInt("grave.max-search-height", 8),
            c.getBoolean("grave.hologram", true),
            xpMode,
            c.getBoolean("grave.return-compass", true)
        );
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("graveguard.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            load();
            if (manager != null) manager.updateConfig(cfg);
            sender.sendMessage("GraveGuard config reloaded.");
            return true;
        }
        sender.sendMessage("/" + label + " reload");
        return true;
    }

    public record Config(long protectionSeconds, long despawnSeconds, int maxSearchHeight,
                         boolean hologram, XpMode xpMode, boolean returnCompass) {}
}
