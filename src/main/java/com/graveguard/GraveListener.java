package com.graveguard;

import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GraveListener implements Listener {
    private final GraveManager mgr;
    public GraveListener(GraveManager mgr) { this.mgr = mgr; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        List<ItemStack> drops = new ArrayList<>(e.getDrops());
        int xp = e.getDroppedExp();
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (!mgr.createGrave(p, drops, xp)) {
            e.getDrops().addAll(drops);
            e.setDroppedExp(xp);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest chest)) return;
        Grave g = mgr.get(chest.getLocation());
        if (g == null || !(e.getPlayer() instanceof Player p)) return;
        e.setCancelled(true);
        if (mgr.protectedFrom(p, g)) {
            mgr.msg(p, mgr.msg("grave-protected")
                .replace("%owner%", g.ownerName)
                .replace("%time%", String.valueOf(g.secondsLeft(System.currentTimeMillis()))));
            return;
        }
        mgr.openByOwner(p, g);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (mgr.get(e.getBlock().getLocation()) == null) return;
        e.setCancelled(true);
        if (e.getPlayer() != null) mgr.msg(e.getPlayer(), "§cYou cannot break a grave.");
    }
}
