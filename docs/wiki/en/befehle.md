# Commands & admin menu

Players need no commands except `/chunkloader` (or `/cl`) - everything goes through menus. For the team there is `/mrchunks`:

| Command | What it does | Permission |
|---|---|---|
| `/mrchunks gui` | Admin menu: all players and their loaders | `mrchunks.admin.gui` |
| `/mrchunks tp <player> <no>` | Teleport to a chunk loader | `mrchunks.admin.teleport` |
| `/mrchunks toggle <player> <no>` | Chunk loader on/off | `mrchunks.admin.toggle` |
| `/mrchunks delete <player> <no>` | Delete a chunk loader | `mrchunks.admin.manage` |
| `/mrchunks unlock <player> <no>` | Lift a lag lock | `mrchunks.admin.manage` |
| `/mrchunks suspendlock <player> <no> <duration\|off>` | [Suspend a loader's limit lock](lag-schutz.md#suspend-the-limit-lock), e.g. `6h` (at most 24 hours), `off` ends it | `mrchunks.admin.suspendlock` |
| `/mrchunks lag` | Show server load and loaders that are disabled due to lag, conspicuous or locked | `mrchunks.admin.manage` |
| `/mrchunks dummy <player> <no>` | A loader's dummy on/off (also beyond the limit) | `mrchunks.admin.manage` |
| `/mrchunks link <main> <alt>` | Link accounts without consent | `mrchunks.admin.link` |
| `/mrchunks unlink <player>` | Remove a link | `mrchunks.admin.link` |
| `/mrchunks limit <player> <budget\|dummy> <amount\|reset>` | Set a player's chunk budget or dummy limit (see [Chunk budget](budget.md)) | `mrchunks.admin.limit` |
| `/mrchunks ban <player> [duration\|permanent] [reason]` | Ban: deletes all loaders and prevents new ones | `mrchunks.admin.ban` |
| `/mrchunks unban <player>` · `bans` | Lift a ban · show all bans | `mrchunks.admin.ban` |
| `/mrchunks discord [test]` | Discord status · test message | `mrchunks.admin.discord` |
| `/mrchunks reload` | Reload config and language file | `mrchunks.admin.reload` |
| `/mrchunks version` | Version and update status | `mrchunks.version` (in `mrchunks.player`) |

Owners may also control their own loaders with `toggle` and `delete`.

## Admin menu
`/mrchunks gui` shows an overview at the top (loaders, active loaders, loaded chunks, bans) and below that all players with loaders as heads.
- **Click on a head:** change the budget with left/right click (±10 chunks, shift = reset), dummy limit (±1), delete all loaders, ban or unban - and below that all of the player's loaders.
- **Click on a loader:** on/off, size, rename, show border, teleport there, delete.

All menus update every second. Every action checks its own permission.

> [!TIP]
> With MRadmin the same is available in the browser: page **Chunk loaders** for the team (with history, live view and control) and the MRchunks section on every player page.
