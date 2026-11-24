package com.graveguard;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class GraveManager {
    private final GraveGuardPlugin plugin;
    private final GraveGuardPlugin.Config cfg;
    private final Map<Location, Grave> graves = new HashMap<>();

    public GraveManager(GraveGuardPlugin plugin, GraveGuardPlugin.Config cfg) {
        this.plugin = plugin; this.cfg = cfg;
    }

    public Grave get(Location loc) { return graves.get(loc); }
    public Collection<Grave> all() { return graves.values(); }

    public boolean createGrave(Player p, List<ItemStack> drops, int xp) {
        Block spot = findSpot(p.getLocation());
        if (spot == null) return false; // world void etc — items drop normally then
        spot.setType(Material.CHEST);
        if (!(spot.getState() instanceof Chest chest)) { spot.setType(Material.AIR); return false; }
        int overflow = 0;
        for (ItemStack it : drops) {
            if (it == null || it.getType().isAir()) continue;
            if (chest.getBlockInventory().firstEmpty() == -1) {
                // keep overflow as natural drops
                p.getWorld().dropItemNaturally(spot.getLocation().add(0.5, 1, 0.5), it);
                overflow++;
            } else chest.getBlockInventory().addItem(it);
        }
        long now = System.currentTimeMillis();
        ArmorStand holo = null;
        if (cfg.hologram()) {
            holo = (ArmorStand) spot.getWorld().spawnEntity(spot.getLocation().add(0.5, 1.0, 0.5), EntityType.ARMOR_STAND);
            holo.setInvisible(true); holo.setMarker(true); holo.setGravity(false); holo.setCustomNameVisible(true);
        }
        Grave g = new Grave(p.getUniqueId(), p.getName(), spot.getLocation(),
                now + cfg.protectionSeconds() * 1000,
                cfg.despawnSeconds() <= 0 ? 0 : now + cfg.despawnSeconds() * 1000,
                cfg.storeXp() ? xp : 0, holo);
        graves.put(spot.getLocation(), g);
        if (cfg.returnCompass()) {
            p.setCompassTarget(spot.getLocation());
            var comp = new ItemStack(Material.COMPASS);
            var meta = comp.getItemMeta();
            if (meta != null) { meta.setDisplayName("§6Last Grave"); comp.setItemMeta(meta); }
            p.getInventory().addItem(comp);
        }
        msg(p, msg("grave-created").replace("%time%", String.valueOf(cfg.protectionSeconds())));
        return true;
    }

    private Block findSpot(Location death) {
        var w = death.getWorld(); if (w == null) return null;
        int bx = death.getBlockX(), bz = death.getBlockZ(), by = death.getBlockY();
        for (int dy = 0; dy <= cfg.maxSearchHeight(); dy++) {
            Block b = w.getBlockAt(bx, Math.min(by + dy, w.getMaxHeight() - 2), bz);
            if (b.getType().isAir() && b.getRelative(BlockFace.DOWN).getType().isSolid()) return b;
            if (!b.getType().isAir() && b.isPassable() && b.getRelative(BlockFace.DOWN).getType().isSolid()) return b;
        }
        // last resort: directly at death spot if the space can hold a block
        Block b = w.getBlockAt(bx, Math.max(w.getMinHeight() + 1, by), bz);
        return (b.getType().isAir() || b.isPassable()) ? b : null;
    }

    public boolean protectedFrom(Player viewer, Grave g) {
        long now = System.currentTimeMillis();
        if (!g.protectedFor(now)) return false;
        if (viewer.getUniqueId().equals(g.owner)) return false;
        return !viewer.hasPermission("graveguard.bypass");
    }

    public void openByOwner(Player p, Grave g) {
        Block b = g.chest.getBlock();
        if (b.getState() instanceof Chest chest) {
            var inv = chest.getBlockInventory();
            for (ItemStack it : inv.getContents()) {
                if (it == null) continue;
                p.getInventory().addItem(it).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(g.chest.clone().add(0.5, 1, 0.5), left));
            }
            inv.clear();
        }
        if (g.xp > 0) p.giveExp(g.xp);
        remove(g, true);
        msg(p, msg("grave-opened"));
    }

    public void despawn(Grave g) {
        Block b = g.chest.getBlock();
        if (b.getState() instanceof Chest chest) {
            for (ItemStack it : chest.getBlockInventory().getContents())
                if (it != null) g.chest.getWorld().dropItemNaturally(g.chest.clone().add(0.5, 0.5, 0.5), it);
        }
        remove(g, true);
        Player owner = plugin.getServer().getPlayer(g.owner);
        if (owner != null) msg(owner, msg("grave-broken").replace("%loc%",
            g.chest.getBlockX() + " " + g.chest.getBlockY() + " " + g.chest.getBlockZ()));
    }

    public void remove(Grave g, boolean clearBlock) {
        if (clearBlock) g.chest.getBlock().setType(Material.AIR);
        if (g.holo != null) g.holo.remove();
        graves.remove(g.chest);
    }

    public void shutdown() {
        for (Grave g : List.copyOf(graves.values())) remove(g, false);
        graves.clear();
    }

    public String msg(String key) { return plugin.getConfig().getString("messages." + key, key); }
    public void msg(Player p, String m) {
        String prefix = plugin.getConfig().getString("prefix", "");
        p.sendMessage((prefix + m).replace('&', '§'));
    }
}
