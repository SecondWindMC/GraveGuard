# GraveGuard — persistent death graves for Paper 1.21.x

On death, items and XP are sealed in a protected grave with a hologram countdown and a return compass. No more despawned loot, chest overflow loss, or restart wipeouts.

**Download:** see [Releases](../../releases) — or on [Hangar](https://hangar.papermc.io/SecondWindMC/GraveGuard).
**Buy / support:** https://secondwindmc.github.io — $4.99
**Docs:** drop the jar in `plugins/`, restart, edit `plugins/GraveGuard/config.yml`.
**Support:** secondwindmc@proton.me

## What is new in 1.1.0

- Graves persist across server restarts through `plugins/GraveGuard/graves.yml`.
- Items are stored virtually in the grave record rather than as lootable chest contents, so overflow is protected too.
- Exact XP preservation is configurable with `grave.xp-mode: FULL|VANILLA_DROP|NONE`.
- The return compass is delivered after respawn instead of being lost with the death inventory.
- Grave blocks are protected from player breaking and explosions.
- `/graveguard reload` now updates the live configuration.

## Validation performed

Validated on Paper 1.21.11 build 132 with Java 21 using automated offline-mode Mineflayer clients:

- death created a chest grave at the death point;
- diamonds and an iron sword were serialized virtually in `graves.yml`;
- 100 XP points were stored exactly as 100;
- a return compass was given after respawn;
- the grave and serialized contents survived a full server restart;
- a second intruder client could not take protected items;
- the owner successfully reclaimed all stored items after the intruder attempt;
- the grave block was removed after the successful claim.

Build from source:

```bash
mvn package   # JDK 21
```
