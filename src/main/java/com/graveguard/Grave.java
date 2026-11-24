package com.graveguard;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.util.UUID;

public final class Grave {
    public final UUID owner;
    public final String ownerName;
    public final Location chest;
    public final long protectedUntilMillis;
    public final long despawnAtMillis; // 0 = never
    public final int xp;
    public ArmorStand holo;

    public Grave(UUID owner, String ownerName, Location chest, long protectedUntil, long despawnAt, int xp, ArmorStand holo) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.chest = chest;
        this.protectedUntilMillis = protectedUntil;
        this.despawnAtMillis = despawnAt;
        this.xp = xp;
        this.holo = holo;
    }

    public boolean protectedFor(long now) { return now < protectedUntilMillis; }
    public long secondsLeft(long now) { return Math.max(0, (protectedUntilMillis - now) / 1000); }
}
