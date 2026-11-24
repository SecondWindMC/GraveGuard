package com.graveguard;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public final class GraveTask extends BukkitRunnable {
    private final GraveManager mgr;
    public GraveTask(GraveManager mgr) { this.mgr = mgr; }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Grave g : List.copyOf(mgr.all())) {
            if (g.holo != null) {
                String s = g.protectedFor(now)
                    ? "§6" + g.ownerName + " §7— protected §c" + g.secondsLeft(now) + "s"
                    : "§6" + g.ownerName + " §7— unprotected";
                g.holo.setCustomName(s);
            }
            if (g.despawnAtMillis > 0 && now >= g.despawnAtMillis) mgr.despawn(g);
        }
    }
}
