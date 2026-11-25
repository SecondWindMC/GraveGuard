package com.graveguard;

import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
        int vanillaXp = e.getDroppedExp();
        int storedXp = switch (mgr.xpMode()) {
            case FULL -> XpUtil.currentExperience(p);
            case VANILLA_DROP -> vanillaXp;
            case NONE -> 0;
        };

        e.getDrops().clear();
        if (mgr.xpMode() != GraveGuardPlugin.XpMode.NONE) e.setDroppedExp(0);
        if (!mgr.createGrave(p, drops, storedXp)) {
            e.getDrops().addAll(drops);
            e.setDroppedExp(vanillaXp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        mgr.handleRespawn(e.getPlayer());
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
        mgr.claim(p, g);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (mgr.get(e.getBlock().getLocation()) == null) return;
        e.setCancelled(true);
        mgr.msg(e.getPlayer(), "§cYou cannot break a grave.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> mgr.get(block.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> mgr.get(block.getLocation()) != null);
    }
}
