package com.graveguard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GraveManager {
    private final GraveGuardPlugin plugin;
    private GraveGuardPlugin.Config cfg;
    private final Map<Location, Grave> graves = new HashMap<>();
    private final File storageFile;

    public GraveManager(GraveGuardPlugin plugin, GraveGuardPlugin.Config cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.storageFile = new File(plugin.getDataFolder(), "graves.yml");
        loadGraves();
    }

    public void updateConfig(GraveGuardPlugin.Config cfg) {
        this.cfg = cfg;
    }

    public Grave get(Location loc) { return graves.get(blockLocation(loc)); }
    public Collection<Grave> all() { return graves.values(); }
    public GraveGuardPlugin.XpMode xpMode() { return cfg.xpMode(); }

    public boolean createGrave(Player p, List<ItemStack> drops, int xp) {
        Block spot = findSpot(p.getLocation());
        if (spot == null) return false;
        spot.setType(Material.CHEST);
        if (!(spot.getState() instanceof Chest chest)) {
            spot.setType(Material.AIR);
            return false;
        }
        chest.getBlockInventory().clear();

        List<ItemStack> savedItems = new ArrayList<>();
        for (ItemStack item : drops) {
            if (item != null && !item.getType().isAir()) savedItems.add(item.clone());
        }

        long now = System.currentTimeMillis();
        Location graveLocation = blockLocation(spot.getLocation());
        ArmorStand holo = cfg.hologram() ? spawnHologram(graveLocation) : null;
        Grave grave = new Grave(
                p.getUniqueId(), p.getName(), graveLocation,
                now + cfg.protectionSeconds() * 1000,
                cfg.despawnSeconds() <= 0 ? 0 : now + cfg.despawnSeconds() * 1000,
                xp, savedItems, holo);
        graves.put(graveLocation, grave);
        saveGraves();
        msg(p, msg("grave-created").replace("%time%", String.valueOf(cfg.protectionSeconds())));
        return true;
    }

    private Block findSpot(Location death) {
        World world = death.getWorld();
        if (world == null) return null;
        int bx = death.getBlockX();
        int bz = death.getBlockZ();
        int by = death.getBlockY();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        for (int dy = 0; dy <= cfg.maxSearchHeight(); dy++) {
            int y = Math.min(by + dy, maxY);
            if (y < minY) continue;
            Block block = world.getBlockAt(bx, y, bz);
            boolean replaceable = block.getType().isAir() || block.isPassable();
            if (replaceable && block.getRelative(BlockFace.DOWN).getType().isSolid()) return block;
        }
        int fallbackY = Math.max(minY, Math.min(by, maxY));
        Block fallback = world.getBlockAt(bx, fallbackY, bz);
        return (fallback.getType().isAir() || fallback.isPassable()) ? fallback : null;
    }

    public boolean protectedFrom(Player viewer, Grave grave) {
        long now = System.currentTimeMillis();
        if (!grave.protectedFor(now)) return false;
        if (viewer.getUniqueId().equals(grave.owner)) return false;
        return !viewer.hasPermission("graveguard.bypass");
    }

    public void claim(Player player, Grave grave) {
        giveItems(player, grave.chest, grave.items);
        if (grave.xp > 0) player.giveExp(grave.xp);
        remove(grave, true);
        saveGraves();
        msg(player, msg("grave-opened"));
    }

    public void despawn(Grave grave) {
        dropItems(grave.chest, grave.items);
        remove(grave, true);
        saveGraves();
        Player owner = plugin.getServer().getPlayer(grave.owner);
        if (owner != null) msg(owner, msg("grave-broken").replace("%loc%",
            grave.chest.getBlockX() + " " + grave.chest.getBlockY() + " " + grave.chest.getBlockZ()));
    }

    public void remove(Grave grave, boolean clearBlock) {
        Block block = grave.chest.getBlock();
        if (clearBlock && block.getType() == Material.CHEST) block.setType(Material.AIR);
        if (grave.holo != null) grave.holo.remove();
        graves.remove(blockLocation(grave.chest));
    }

    public void handleRespawn(Player player) {
        if (!cfg.returnCompass()) return;
        Grave latest = null;
        for (Grave grave : graves.values()) {
            if (!grave.owner.equals(player.getUniqueId())) continue;
            if (latest == null || grave.protectedUntilMillis > latest.protectedUntilMillis) latest = grave;
        }
        if (latest == null) return;
        Grave selected = latest;
        player.setCompassTarget(selected.chest);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Grave current = get(selected.chest);
            if (current == null || current.compassGiven || !current.owner.equals(player.getUniqueId()) || !player.isOnline()) return;
            current.compassGiven = true;
            player.setCompassTarget(current.chest);
            ItemStack compass = new ItemStack(Material.COMPASS);
            var meta = compass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6Last Grave");
                compass.setItemMeta(meta);
            }
            player.getInventory().addItem(compass).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }, 2L);
    }

    private void giveItems(Player player, Location location, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            player.getInventory().addItem(item.clone()).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(location.clone().add(0.5, 1, 0.5), left));
        }
    }

    private void dropItems(Location location, List<ItemStack> items) {
        World world = location.getWorld();
        if (world == null) return;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                world.dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), item.clone());
            }
        }
    }

    private ArmorStand spawnHologram(Location chestLocation) {
        ArmorStand holo = (ArmorStand) chestLocation.getWorld().spawnEntity(
                chestLocation.clone().add(0.5, 1.0, 0.5), EntityType.ARMOR_STAND);
        holo.setInvisible(true);
        holo.setMarker(true);
        holo.setGravity(false);
        holo.setCustomNameVisible(true);
        return holo;
    }

    public void shutdown() {
        saveGraves();
        for (Grave grave : graves.values()) {
            if (grave.holo != null) grave.holo.remove();
        }
        graves.clear();
    }

    private void loadGraves() {
        if (!storageFile.isFile()) return;
        YamlConfiguration disk = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = disk.getConfigurationSection("graves");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            String base = "graves." + key;
            World world = null;
            String worldId = disk.getString(base + ".world");
            if (worldId != null) {
                try { world = plugin.getServer().getWorld(UUID.fromString(worldId)); }
                catch (IllegalArgumentException ignored) {}
            }
            if (world == null) {
                String worldName = disk.getString(base + ".world-name");
                if (worldName != null) world = plugin.getServer().getWorld(worldName);
            }
            if (world == null) continue;

            Location requested = new Location(world,
                    disk.getInt(base + ".x"), disk.getInt(base + ".y"), disk.getInt(base + ".z"));
            Block spot = ensureChestMarker(requested);
            if (spot == null) continue;

            UUID owner;
            try { owner = UUID.fromString(disk.getString(base + ".owner", "")); }
            catch (IllegalArgumentException ex) { continue; }
            String ownerName = disk.getString(base + ".owner-name", "unknown");
            List<ItemStack> items = new ArrayList<>();
            for (Object raw : disk.getList(base + ".items", List.of())) {
                if (raw instanceof ItemStack item) items.add(item.clone());
            }
            Location location = blockLocation(spot.getLocation());
            ArmorStand holo = cfg.hologram() ? spawnHologram(location) : null;
            Grave grave = new Grave(owner, ownerName, location,
                    disk.getLong(base + ".protected-until"), disk.getLong(base + ".despawn-at"),
                    disk.getInt(base + ".xp"), items, holo);
            grave.compassGiven = disk.getBoolean(base + ".compass-given", true);
            graves.put(location, grave);
        }
    }

    private Block ensureChestMarker(Location requested) {
        Block existing = requested.getBlock();
        if (existing.getType() == Material.CHEST) return existing;
        if (existing.getType().isAir() || existing.isPassable()) {
            existing.setType(Material.CHEST);
            return existing;
        }
        World world = requested.getWorld();
        if (world == null) return null;
        for (int dy = 1; dy <= cfg.maxSearchHeight(); dy++) {
            int y = requested.getBlockY() + dy;
            if (y >= world.getMaxHeight() - 1) break;
            Block candidate = world.getBlockAt(requested.getBlockX(), y, requested.getBlockZ());
            if (candidate.getType().isAir() || candidate.isPassable()) {
                candidate.setType(Material.CHEST);
                return candidate;
            }
        }
        return null;
    }

    private void saveGraves() {
        YamlConfiguration disk = new YamlConfiguration();
        int index = 0;
        for (Grave grave : graves.values()) {
            String base = "graves." + index++;
            World world = grave.chest.getWorld();
            if (world == null) continue;
            disk.set(base + ".world", world.getUID().toString());
            disk.set(base + ".world-name", world.getName());
            disk.set(base + ".x", grave.chest.getBlockX());
            disk.set(base + ".y", grave.chest.getBlockY());
            disk.set(base + ".z", grave.chest.getBlockZ());
            disk.set(base + ".owner", grave.owner.toString());
            disk.set(base + ".owner-name", grave.ownerName);
            disk.set(base + ".protected-until", grave.protectedUntilMillis);
            disk.set(base + ".despawn-at", grave.despawnAtMillis);
            disk.set(base + ".xp", grave.xp);
            disk.set(base + ".compass-given", grave.compassGiven);
            disk.set(base + ".items", grave.items);
        }
        try {
            File parent = storageFile.getParentFile();
            if (parent != null) parent.mkdirs();
            disk.save(storageFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save graves.yml: " + ex.getMessage());
        }
    }

    private Location blockLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String msg(String key) { return plugin.getConfig().getString("messages." + key, key); }
    public void msg(Player player, String message) {
        String prefix = plugin.getConfig().getString("prefix", "");
        player.sendMessage((prefix + message).replace('&', '§'));
    }
}
