# Dummy players

A chunk loader keeps chunks loaded - but for the server there is **no player** there. That is why mobs are almost inactive and nothing spawns. A **dummy** is an artificial player who sits on the loader and counts as a player nearby.

- **Switch on:** the loader's menu → **Switch dummy on**. It sits there as long as the loader is active.
- **Looks:** your skin, name `[D<no>]<your name>`, sitting.
- **Otherwise no player:** invulnerable, ignored by mobs, not in the tab list and not in the player count. MRsleep, MRadmin and MRpvp ignore it.
- **Limit:** 1 per player by default, more per permission or admin command.
- **Only on square loaders from 5×5, block in the middle:** like a player, a dummy simulates a square around itself. With a rectangle or a smaller loader it would simulate chunks outside the area that your budget does not pay for. That is why, while a dummy sits on the loader, its size can only be changed evenly in all directions.

> [!TIP]
> The dummy button is gray? In the menu **Settings → Size** click the middle so the loader becomes square (at least 5×5).

## For admins
> [!NOTE]
> Dummies are **off by default** (`dummies.enabled: false`). A dummy costs as much performance as a real player at that spot. It uses Paper server internals: if the MRchunks version does not exactly match the server version, dummies stay off (notice in the console) - everything else works normally.

- Limit: `dummies.default-limit`, permission `mrchunks.dummies.<n>` or `mrchunks.dummies.unlimited`, or `/mrchunks limit <player> dummy <n>` (see [Chunk budget](budget.md)).
- More settings under `dummies` in the [configuration](konfiguration.md) (skin, sitting).
- A dummy does not block unloading a world: with `/mv unload <world> --remove-players` it leaves the world and sits back on its loader when the world is loaded.
