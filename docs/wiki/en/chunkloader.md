# Using chunk loaders

## How it works
1. **`/chunkloader`** (or `/cl`) opens your menu. At the bottom right, **Get chunk loaders** puts a loader into your inventory (left click 1, shift up to 64 - at most 30 loaders in total). Your **budget** is shown there as well. **Return chunk loaders** next to it takes items back.
2. **Place the block** - from now on the `7×7` chunks around it stay loaded. If your free budget is not enough for 7×7, the loader becomes as big as still possible.
3. **Right click** on the block opens its menu: info at the top, below **Members**, **on/off**, **Show border** (ender pearl), **Dummy** and **Settings**. The settings contain **Size**, **Change block**, **Rename** and **Delete**. **Sneak + right click** behaves normally, so you can keep building against it.
4. **Deleting** only works via the menu (click twice). Deleted is final.

The block is indestructible: no mining, no explosions, pistons, withers or endermen.

## The menu bar in /chunkloader
From left to right:

| Button | What for |
|---|---|
| **Members for all loaders** | Invite players to all your loaders - see [Friends](mitspieler.md) |
| **Shared with me** | Other players' loaders you are invited to |
| **Linked accounts** | Link or unlink an alt account |
| **Web map** | Link to the map of the player page in chat (only with MRadmin) |
| **Get chunk loaders** / **Return chunk loaders** | Get or return loader items |

If you have more than 45 loaders, arrows for paging appear. Depending on the server, buttons you lack the permissions for are missing.

## What the block in the menu means
| Block | Status |
|---|---|
| Emerald block | Everything OK |
| Gray concrete | Switched off |
| Magma block | Above a warn limit of the lag protection - the farm is conspicuous right now |
| Redstone block | Locked because of lag - only the team can unlock it |
| Orange concrete | Disabled due to lag: stays on, loads again by itself |
| Cobweb | Paused because the whole server lags or the team paused all loaders |

The menu of every active loader shows its last measurement against its limits - so you can judge your farm yourself.

## What runs in loaded chunks?
- **Runs:** redstone, hoppers, furnaces, animals and other entities, plant growth - everything that needs random ticks.
- **Still needs a player nearby:** natural mob spawning, spawners and full mob AI (without a player mobs hardly move and e.g. do not walk through portals). That is what [dummy players](dummy.md) are for.

## Names, block and border
- Every loader gets the **smallest free number** ("Chunk loader 4"). With **Rename** you give it its own name - it appears in chat, in menus, on the map and in Discord.
- **Change block** chooses which block your loader is in the world (e.g. diamond, gold or netherite block). Only the block in the world changes - in menus and as an item it stays the emerald block. The server decides which blocks are available.
- **Show border** draws a glowing fence at the outer edge of the area, visible through blocks and only for you. It disappears by itself after a while.
