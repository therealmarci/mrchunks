# Chunk budget

Every player has a **budget in chunks**: this many chunks all of their **active** loaders may keep loaded together. How they split it - a few big or many small loaders, squares or rectangles - they decide themselves in the menu **Size** (see [Size and chunk budget](groesse.md)).

| | Chunk budget |
|---|---|
| Default (config) | `loaders.default-budget` (147 = three 7×7 loaders) |
| Per permission | `mrchunks.budget.<n>` |
| Unlimited | `mrchunks.budget.unlimited` |
| Per admin | `/mrchunks limit <player> budget <n\|reset>`, admin menu or MRadmin web interface |

- **Cost:** an active loader costs width × length, so 49 for 7×7. Switched-off loaders cost nothing. Overlapping chunks count fully for each loader.
- **New loaders** have the size `loaders.default-reach` (3 = 7×7). If the free budget is not enough, they get the biggest square that still fits; if nothing at all is free, they are placed but stay **off**.
- **Maximum size:** `loaders.max-reach` chunks per direction (default 7 = up to 15×15).
- **How many loaders** someone may place is the same for everyone: `loaders.max-loaders` (default 30).
- **Nobody exceeds the budget** - not even the team when switching on or enlarging someone else's loaders. Admins raise the budget instead.
- If an admin lowers the budget, surplus loaders are switched off right away (the last placed first) and the owner is informed.
- **Order:** an admin value takes precedence, then the highest permission number counts, but at least the default from the config.
- **Dummies** have their own limit following the same pattern (`dummies.default-limit`, `mrchunks.dummies.<n>`, `/mrchunks limit <player> dummy <n>`).

## Overlapping loaders
`loaders.overlap`: `allow` simply allows it, `warn` (default) allows it with a notice and shows the border, `deny-own` forbids overlaps with your own loaders, `deny-all` with all - when placing and when enlarging. Every chunk is only kept loaded **once** anyway, but in the budget it counts for each loader.

## Examples
```text
/lp group vip permission set mrchunks.budget.300 true       # VIPs: 300 chunks (e.g. six 7x7 loaders)
/mrchunks limit Steve budget 200                             # give a player 200 chunks
/mrchunks limit Steve budget reset                           # back to permission or default
```
