package com.graveguard;

import org.bukkit.entity.Player;

final class XpUtil {
    private XpUtil() {}

    static int currentExperience(Player player) {
        int level = Math.max(0, player.getLevel());
        int totalAtLevel = experienceForLevel(level);
        int progress = Math.round(player.getExp() * experienceToNextLevel(level));
        return totalAtLevel + progress;
    }

    static int experienceForLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (5 * level * level - 81 * level + 720) / 2;
        return (9 * level * level - 325 * level + 4440) / 2;
    }

    static int experienceToNextLevel(int level) {
        if (level >= 31) return 9 * level - 158;
        if (level >= 16) return 5 * level - 38;
        return 2 * level + 7;
    }
}
